package com.therapy.pack.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PackTypeResponse {
    private UUID id;
    private String name;
    private int sessionCount;
    private BigDecimal priceAmount;
    private String priceCurrency;
    private int validityDays;
    private String description;
}
