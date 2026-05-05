package com.buythedip.backend.health.dto;

import java.time.Instant;

public record HealthResponse(String status, String service, Instant timestamp) {

    public static HealthResponse up() {
        return new HealthResponse("UP", "buythedip-backend", Instant.now());
    }
}
