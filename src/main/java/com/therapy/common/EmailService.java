package com.therapy.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Async
    public void sendOtp(String toEmail, String recipientName, String otp) {
        if (from.isBlank() || mailPassword.isBlank()) {
            log.warn("⚠️  MAIL credentials not configured — OTP for {} : {}", toEmail, otp);
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Tu código de verificación — TherapyAI");
            helper.setText(buildOtpHtml(recipientName, otp), true);
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", toEmail, e);
        }
    }

    private String buildOtpHtml(String name, String otp) {
        return """
            <div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
              <h2 style="color:#4f46e5;margin-bottom:8px">TherapyAI</h2>
              <p style="color:#374151">Hola %s,</p>
              <p style="color:#374151">Recibimos una solicitud para restablecer la contraseña de tu cuenta.</p>
              <p style="color:#374151">Tu código de verificación es:</p>
              <div style="background:#f3f4f6;border-radius:12px;padding:24px;text-align:center;margin:24px 0">
                <span style="font-size:36px;font-weight:700;letter-spacing:12px;color:#111827">%s</span>
              </div>
              <p style="color:#6b7280;font-size:13px">
                Este código expira en <strong>15 minutos</strong>.<br>
                Si no solicitaste el cambio de contraseña, podés ignorar este email.
              </p>
            </div>
            """.formatted(name, otp);
    }
}
