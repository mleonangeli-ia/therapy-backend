package com.therapy.pack;

import com.therapy.common.exception.AppException;
import com.therapy.pack.dto.PackResponse;
import com.therapy.pack.dto.PackTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PackService {

    private final PackRepository packRepository;
    private final PackTypeRepository packTypeRepository;

    public List<PackTypeResponse> getActivePackTypes() {
        return packTypeRepository.findByIsActiveTrueOrderByPriceAmountAsc()
                .stream()
                .map(this::toPackTypeResponse)
                .toList();
    }

    public List<PackResponse> getPatientPacks(UUID patientId) {
        return packRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(this::toPackResponse)
                .toList();
    }

    public Optional<PackResponse> getActivePack(UUID patientId) {
        return packRepository.findActivePackByPatientId(patientId)
                .map(this::toPackResponse);
    }

    public PackResponse getPack(UUID packId, UUID patientId) {
        Pack pack = packRepository.findById(packId)
                .orElseThrow(() -> AppException.notFound("Pack"));
        if (!pack.getPatient().getId().equals(patientId)) {
            throw AppException.forbidden();
        }
        return toPackResponse(pack);
    }

    @Transactional
    public Pack consumeSession(UUID packId, UUID patientId) {
        Pack pack = packRepository.findById(packId)
                .orElseThrow(() -> AppException.notFound("Pack"));

        if (!pack.getPatient().getId().equals(patientId)) {
            throw AppException.forbidden();
        }
        if (!pack.canStartSession()) {
            throw AppException.badRequest("No se puede iniciar sesión: pack sin sesiones disponibles o vencido");
        }

        pack.setSessionsUsed(pack.getSessionsUsed() + 1);
        if (pack.getSessionsRemaining() == 0) {
            pack.setStatus(PackStatus.COMPLETED);
        }
        return packRepository.save(pack);
    }

    PackTypeResponse toPackTypeResponse(PackType pt) {
        return PackTypeResponse.builder()
                .id(pt.getId())
                .name(pt.getName())
                .sessionCount(pt.getSessionCount())
                .priceAmount(pt.getPriceAmount())
                .priceCurrency(pt.getPriceCurrency())
                .validityDays(pt.getValidityDays())
                .description(pt.getDescription())
                .build();
    }

    PackResponse toPackResponse(Pack pack) {
        return PackResponse.builder()
                .id(pack.getId())
                .status(pack.getStatus())
                .sessionsUsed(pack.getSessionsUsed())
                .sessionsTotal(pack.getSessionsTotal())
                .sessionsRemaining(pack.getSessionsRemaining())
                .purchasedAt(pack.getPurchasedAt())
                .activatedAt(pack.getActivatedAt())
                .expiresAt(pack.getExpiresAt())
                .packType(toPackTypeResponse(pack.getPackType()))
                .build();
    }
}
