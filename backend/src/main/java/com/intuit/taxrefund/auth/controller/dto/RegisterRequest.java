package com.intuit.taxrefund.auth.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 10, max = 72) String password,

    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,

    // optional
    @Size(max = 255) String address,

    @NotBlank @Size(max = 100) String city,

    // 2-letter state code (e.g., CA)
    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "state must be a 2-letter code")
    String state,

    @Size(max = 30) String phone
) {
}