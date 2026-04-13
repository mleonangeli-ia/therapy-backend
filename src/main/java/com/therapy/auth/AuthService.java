package com.therapy.auth;

import com.therapy.auth.dto.*;
import com.therapy.common.EmailService;
import com.therapy.common.audit.AuditService;
import com.therapy.common.exception.AppException;
import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final String CONSENT_CURRENT_VERSION = "1.0";

    private static final int OTP_EXPIRY_MINUTES = 15;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PatientRepository patientRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (patientRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw AppException.conflict("El email ya está registrado");
        }

        Patient patient = Patient.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .countryCode(request.getCountryCode())
                .build();

        patient = patientRepository.save(patient);

        auditService.log("PATIENT_REGISTERED", patient.getId(), "patient", patient.getId(), ipAddress, userAgent);

        return buildAuthResponse(patient, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Patient patient = patientRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> AppException.unauthorized("Credenciales inválidas"));

        if (!patient.isActive()) {
            throw AppException.unauthorized("Cuenta desactivada");
        }

        if (patient.isLocked()) {
            throw AppException.unauthorized("Cuenta bloqueada temporalmente. Intente más tarde.");
        }

        if (!passwordEncoder.matches(request.getPassword(), patient.getPasswordHash())) {
            handleFailedLogin(patient);
            throw AppException.unauthorized("Credenciales inválidas");
        }

        // Reset failed attempts on successful login
        patient.setFailedLoginAttempts(0);
        patient.setLockedUntil(null);
        patient.setLastLoginAt(OffsetDateTime.now());
        patientRepository.save(patient);

        auditService.log("PATIENT_LOGIN", patient.getId(), "patient", patient.getId(), ipAddress, userAgent);

        return buildAuthResponse(patient, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, String ipAddress) {
        String tokenHash = hashToken(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> AppException.unauthorized("Token de refresh inválido"));

        if (!refreshToken.isValid()) {
            throw AppException.unauthorized("Token de refresh expirado o revocado");
        }

        // Rotate: revoke old token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        Patient patient = refreshToken.getPatient();
        return buildAuthResponse(patient, ipAddress, null);
    }

    @Transactional
    public void logout(UUID patientId) {
        refreshTokenRepository.revokeAllByPatientId(patientId);
        auditService.log("PATIENT_LOGOUT", patientId, "patient", patientId, null, null);
    }

    @Transactional
    public void signConsent(UUID patientId, ConsentRequest request, String ipAddress, String userAgent) {
        if (!request.isAccepted()) {
            throw AppException.badRequest("Debe aceptar los términos para continuar");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Paciente"));

        patient.setConsentSignedAt(OffsetDateTime.now());
        patient.setConsentVersion(CONSENT_CURRENT_VERSION);
        patientRepository.save(patient);

        auditService.log("CONSENT_SIGNED", patientId, "patient", patientId, ipAddress, userAgent);
    }

    /**
     * Step 1: generate 6-digit OTP, store hashed with "OTP:" prefix, send email.
     * Returns 404 if email is not registered.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        Patient patient = patientRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> AppException.badRequest("No existe una cuenta con ese email"));

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String stored = "OTP:" + hashToken(otp);
        patient.setResetPasswordToken(stored);
        patient.setResetPasswordTokenExpiry(OffsetDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        patientRepository.save(patient);
        emailService.sendOtp(patient.getEmail(), patient.getFullName().split(" ")[0], otp);
    }

    /**
     * Step 2: verify OTP, if valid exchange it for a short-lived reset token.
     * Returns resetToken on success.
     */
    @Transactional
    public Map<String, String> verifyOtp(VerifyOtpRequest request) {
        Patient patient = patientRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> AppException.badRequest("Código inválido o expirado"));

        String stored = patient.getResetPasswordToken();
        OffsetDateTime expiry = patient.getResetPasswordTokenExpiry();

        if (stored == null || !stored.startsWith("OTP:")
                || expiry == null || expiry.isBefore(OffsetDateTime.now())) {
            throw AppException.badRequest("Código inválido o expirado");
        }

        String expectedHash = "OTP:" + hashToken(request.getOtp());
        if (!stored.equals(expectedHash)) {
            throw AppException.badRequest("Código inválido o expirado");
        }

        // Exchange OTP for a reset token
        String rawResetToken = UUID.randomUUID().toString();
        patient.setResetPasswordToken("RESET:" + hashToken(rawResetToken));
        patient.setResetPasswordTokenExpiry(OffsetDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
        patientRepository.save(patient);

        return Map.of("resetToken", rawResetToken);
    }

    /**
     * Step 3: reset the password using the reset token.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String expectedHash = "RESET:" + hashToken(request.getResetToken());

        Patient patient = patientRepository.findByResetPasswordToken(expectedHash)
                .orElseThrow(() -> AppException.badRequest("Token de restablecimiento inválido o expirado"));

        if (patient.getResetPasswordTokenExpiry() == null
                || patient.getResetPasswordTokenExpiry().isBefore(OffsetDateTime.now())) {
            throw AppException.badRequest("Token de restablecimiento inválido o expirado");
        }

        patient.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        patient.setResetPasswordToken(null);
        patient.setResetPasswordTokenExpiry(null);
        patient.setFailedLoginAttempts(0);
        patient.setLockedUntil(null);
        patientRepository.save(patient);

        // Revoke all refresh tokens for security
        refreshTokenRepository.revokeAllByPatientId(patient.getId());
        log.info("Password reset for patient {}", patient.getEmail());
    }

    private AuthResponse buildAuthResponse(Patient patient, String ipAddress, String userAgent) {
        String accessToken = jwtService.generateAccessToken(patient.getId(), patient.getEmail());
        String rawRefreshToken = jwtService.generateRefreshToken(patient.getId());

        // Store hashed refresh token
        RefreshToken refreshToken = RefreshToken.builder()
                .patient(patient)
                .tokenHash(hashToken(rawRefreshToken))
                .ipAddress(ipAddress)
                .deviceInfo(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900)  // 15 minutes in seconds
                .patientId(patient.getId())
                .fullName(patient.getFullName())
                .email(patient.getEmail())
                .consentRequired(!patient.hasConsent())
                .build();
    }

    private void handleFailedLogin(Patient patient) {
        int attempts = patient.getFailedLoginAttempts() + 1;
        patient.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            patient.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Account locked for patient {} after {} failed attempts", patient.getEmail(), attempts);
        }
        patientRepository.save(patient);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
