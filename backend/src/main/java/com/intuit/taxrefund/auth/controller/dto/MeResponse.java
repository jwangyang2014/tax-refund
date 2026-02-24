package com.intuit.taxrefund.auth.controller.dto;

public record MeResponse(
        Long userId,
        String email,
        String password
) {
}
