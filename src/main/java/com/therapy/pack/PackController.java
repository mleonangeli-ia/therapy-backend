package com.therapy.pack;

import com.therapy.pack.dto.PackResponse;
import com.therapy.pack.dto.PackTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PackController {

    private final PackService packService;

    @GetMapping("/pack-types")
    public ResponseEntity<List<PackTypeResponse>> listPackTypes() {
        return ResponseEntity.ok(packService.getActivePackTypes());
    }

    @GetMapping("/packs")
    public ResponseEntity<List<PackResponse>> listMyPacks(@AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(packService.getPatientPacks(patientId));
    }

    @GetMapping("/packs/active")
    public ResponseEntity<PackResponse> getActivePack(@AuthenticationPrincipal UUID patientId) {
        return packService.getActivePack(patientId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/packs/{packId}")
    public ResponseEntity<PackResponse> getPack(
            @PathVariable UUID packId,
            @AuthenticationPrincipal UUID patientId) {
        return ResponseEntity.ok(packService.getPack(packId, patientId));
    }
}
