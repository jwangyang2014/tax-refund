package com.intuit.taxrefund.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name too long")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name too long")
    String lastName,

    @Size(max = 255, message = "Address too long")
    String address,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City too long")
    String city,

    @NotBlank(message = "State is required")
    @Size(min = 2, max = 2, message = "State must be 2 letters")
    String state,

    @Size(max = 30, message = "Phone too long")
    String phone
) {}