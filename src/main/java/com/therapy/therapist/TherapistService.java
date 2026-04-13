package com.therapy.therapist;

import com.therapy.auth.JwtService;
import com.therapy.common.exception.AppException;
import com.therapy.therapist.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TherapistService {

    private final TherapistRepository therapistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public TherapistAuthResponse register(TherapistRegisterRequest request) {
        if (therapistRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("El email ya está registrado.");
        }

        Therapist therapist = Therapist.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .licenseNumber(request.getLicenseNumber())
                .build();

        therapist = therapistRepository.save(therapist);
        String token = jwtService.generateTherapistAccessToken(therapist.getId(), therapist.getEmail());

        return TherapistAuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getRefreshTokenExpiryMs() / 1000)
                .therapistId(therapist.getId())
                .fullName(therapist.getFullName())
                .email(therapist.getEmail())
                .build();
    }

    public TherapistAuthResponse login(TherapistLoginRequest request) {
        Therapist therapist = therapistRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> AppException.unauthorized("Credenciales inválidas."));

        if (!therapist.isActive()) {
            throw AppException.unauthorized("Cuenta desactivada.");
        }

        if (!passwordEncoder.matches(request.getPassword(), therapist.getPasswordHash())) {
            throw AppException.unauthorized("Credenciales inválidas.");
        }

        String token = jwtService.generateTherapistAccessToken(therapist.getId(), therapist.getEmail());

        return TherapistAuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getRefreshTokenExpiryMs() / 1000)
                .therapistId(therapist.getId())
                .fullName(therapist.getFullName())
                .email(therapist.getEmail())
                .build();
    }
}
