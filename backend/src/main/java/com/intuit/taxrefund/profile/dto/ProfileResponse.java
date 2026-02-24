package com.intuit.taxrefund.profile.dto;

public record ProfileResponse(
    Long userId,
    String email,
    String role,
    String firstName,
    String lastName,
    String address,
    String city,
    String state,
    String phone
) {}