package com.intuit.taxrefundstatus.auth.api.dto;

public record MeResponse(
        Long userId,
        String email,
        String password
) {
}
