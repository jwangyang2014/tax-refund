package com.intuit.taxrefund.auth.controller.dto;

public record SessionResponse(
        Long userId,
        String email,
        String password
) {
}
