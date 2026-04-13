package com.therapy.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EndSessionRequest {

    @Min(1) @Max(10)
    private Short moodEnd;
}
