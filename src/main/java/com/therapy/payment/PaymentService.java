package com.therapy.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.therapy.common.audit.AuditService;
import com.therapy.common.exception.AppException;
import com.therapy.pack.Pack;
import com.therapy.pack.PackRepository;
import com.therapy.pack.PackStatus;
import com.therapy.pack.PackType;
import com.therapy.pack.PackTypeRepository;
import com.therapy.patient.Patient;
import com.therapy.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PackRepository packRepository;
    private final PackTypeRepository packTypeRepository;
    private final PatientRepository patientRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${mercadopago.access-token}")
    private String mpAccessToken;

    @Value("${mercadopago.success-url}")
    private String successUrl;

    @Value("${mercadopago.failure-url}")
    private String failureUrl;

    @Value("${mercadopago.pending-url}")
    private String pendingUrl;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @Transactional
    public Map<String, String> createPreference(UUID patientId, UUID packTypeId) {
        MercadoPagoConfig.setAccessToken(mpAccessToken);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Paciente"));
        PackType packType = packTypeRepository.findById(packTypeId)
                .orElseThrow(() -> AppException.notFound("Tipo de pack"));

        if (!packType.isActive()) {
            throw AppException.badRequest("El pack seleccionado no está disponible");
        }

        // Create pending pack record
        Pack pack = Pack.builder()
                .patient(patient)
                .packType(packType)
                .sessionsTotal(packType.getSessionCount())
                .status(PackStatus.PENDING_PAYMENT)
                .mpExternalRef(UUID.randomUUID().toString())
                .build();
        pack = packRepository.save(pack);

        try {
            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .id(packType.getId().toString())
                    .title(packType.getName())
                    .quantity(1)
                    .unitPrice(packType.getPriceAmount())
                    .currencyId(packType.getPriceCurrency())
                    .description(packType.getDescription())
                    .build();

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(List.of(item))
                    .payer(PreferencePayerRequest.builder().email(patient.getEmail()).build())
                    .externalReference(pack.getMpExternalRef())
                    .backUrls(PreferenceBackUrlsRequest.builder()
                            .success(successUrl + "?pack=" + pack.getId())
                            .failure(failureUrl + "?pack=" + pack.getId())
                            .pending(pendingUrl + "?pack=" + pack.getId())
                            .build())
                    .autoReturn("approved")
                    .notificationUrl(notificationUrl)
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);

            pack.setMpPreferenceId(preference.getId());
            packRepository.save(pack);

            // Log event
            savePaymentEvent(pack, patient, PaymentEvent.EventType.PREFERENCE_CREATED, null, null, null, null);
            auditService.log("PAYMENT_PREFERENCE_CREATED", patientId, "pack", pack.getId(), null, null);

            return Map.of(
                    "packId", pack.getId().toString(),
                    "preferenceId", preference.getId(),
                    "initPoint", preference.getInitPoint()
            );

        } catch (MPException | MPApiException e) {
            log.error("MercadoPago error creating preference for patient {}", patientId, e);
            throw AppException.badRequest("Error al crear la preferencia de pago");
        }
    }

    @Transactional
    public void processWebhook(Map<String, Object> payload, String signature) {
        // Note: In production, validate the signature before processing
        // See: https://www.mercadopago.com.ar/developers/en/docs/notifications/webhooks/webhooks-configurations

        String topic = (String) payload.get("topic");
        String type = (String) payload.get("type");

        if (!"payment".equals(topic) && !"payment".equals(type)) {
            log.debug("Ignoring non-payment webhook: topic={}, type={}", topic, type);
            return;
        }

        Object resourceId = payload.getOrDefault("data", Map.of()).equals(Map.of())
                ? payload.get("id")
                : ((Map<?, ?>) payload.get("data")).get("id");

        if (resourceId == null) {
            log.warn("No payment ID in webhook payload");
            return;
        }

        String paymentId = resourceId.toString();
        String idempotencyKey = "mp:payment:" + paymentId;

        if (paymentEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate webhook for payment {}, skipping", paymentId);
            return;
        }

        MercadoPagoConfig.setAccessToken(mpAccessToken);

        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(paymentId));

            String externalRef = payment.getExternalReference();
            if (externalRef == null) {
                log.warn("Payment {} has no external reference", paymentId);
                return;
            }

            Pack pack = packRepository.findByMpExternalRef(externalRef)
                    .orElseGet(() -> {
                        log.warn("No pack found for external_ref {}", externalRef);
                        return null;
                    });

            if (pack == null) return;

            Patient patient = pack.getPatient();
            String status = payment.getStatus();

            switch (status) {
                case "approved" -> activatePack(pack, patient, payment, idempotencyKey);
                case "rejected", "cancelled" ->
                        savePaymentEvent(pack, patient, PaymentEvent.EventType.PAYMENT_REJECTED,
                                paymentId, status, payment.getTransactionAmount(), payment.getCurrencyId());
                case "in_process", "pending" ->
                        savePaymentEvent(pack, patient, PaymentEvent.EventType.PAYMENT_PENDING,
                                paymentId, status, null, null);
                default -> log.info("Unhandled payment status: {} for payment {}", status, paymentId);
            }

        } catch (MPException | MPApiException e) {
            log.error("Error fetching payment {} from MercadoPago", paymentId, e);
        }
    }

    @Transactional
    public Map<String, String> mockPurchase(UUID patientId, UUID packTypeId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> AppException.notFound("Paciente"));
        PackType packType = packTypeRepository.findById(packTypeId)
                .orElseThrow(() -> AppException.notFound("Tipo de pack"));

        if (!packType.isActive()) {
            throw AppException.badRequest("El pack seleccionado no está disponible");
        }

        Pack pack = Pack.builder()
                .patient(patient)
                .packType(packType)
                .sessionsTotal(packType.getSessionCount())
                .status(PackStatus.ACTIVE)
                .mpExternalRef("mock-" + UUID.randomUUID())
                .mpPaymentId("mock-payment")
                .mpPaymentStatus("approved")
                .purchasedAt(OffsetDateTime.now())
                .activatedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusDays(packType.getValidityDays()))
                .build();
        packRepository.save(pack);

        auditService.log("PACK_MOCK_PURCHASE", patientId, "pack", pack.getId(), null, null);
        log.info("Mock purchase: pack {} activated for patient {}", pack.getId(), patientId);

        return Map.of(
                "packId", pack.getId().toString(),
                "status", "ACTIVE",
                "sessionsTotal", String.valueOf(pack.getSessionsTotal())
        );
    }

    public Map<String, String> getPaymentStatus(UUID packId, UUID patientId) {
        Pack pack = packRepository.findById(packId)
                .orElseThrow(() -> AppException.notFound("Pack"));
        if (!pack.getPatient().getId().equals(patientId)) {
            throw AppException.forbidden();
        }
        return Map.of(
                "packId", packId.toString(),
                "status", pack.getStatus().name()
        );
    }

    private void activatePack(Pack pack, Patient patient, Payment payment, String idempotencyKey) {
        pack.setStatus(PackStatus.ACTIVE);
        pack.setMpPaymentId(payment.getId().toString());
        pack.setMpPaymentStatus("approved");
        pack.setActivatedAt(OffsetDateTime.now());
        pack.setPurchasedAt(OffsetDateTime.now());
        pack.setExpiresAt(OffsetDateTime.now().plusDays(pack.getPackType().getValidityDays()));
        packRepository.save(pack);

        savePaymentEvent(pack, patient, PaymentEvent.EventType.PAYMENT_APPROVED,
                payment.getId().toString(), "approved",
                payment.getTransactionAmount(), payment.getCurrencyId());
        auditService.log("PACK_ACTIVATED", patient.getId(), "pack", pack.getId(), null, null);

        log.info("Pack {} activated for patient {}", pack.getId(), patient.getId());
    }

    private void savePaymentEvent(Pack pack, Patient patient, PaymentEvent.EventType eventType,
                                   String mpPaymentId, String mpStatus,
                                   BigDecimal amount, String currency) {
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .pack(pack)
                    .patient(patient)
                    .eventType(eventType)
                    .mpPaymentId(mpPaymentId)
                    .mpStatus(mpStatus)
                    .amount(amount)
                    .currency(currency)
                    .idempotencyKey(mpPaymentId != null ? "mp:payment:" + mpPaymentId : null)
                    .build();
            paymentEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save payment event {}", eventType, e);
        }
    }
}
