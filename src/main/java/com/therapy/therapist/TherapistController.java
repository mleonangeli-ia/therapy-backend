package com.therapy.therapist;

import com.therapy.therapist.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/therapist")
@RequiredArgsConstructor
public class TherapistController {

    private final TherapistService therapistService;

    @PostMapping("/register")
    public ResponseEntity<TherapistAuthResponse> register(
            @Valid @RequestBody TherapistRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(therapistService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TherapistAuthResponse> login(
            @Valid @RequestBody TherapistLoginRequest request) {
        return ResponseEntity.ok(therapistService.login(request));
    }
}
