package com.intuit.taxrefundstatus.auth.service;

import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 10 || password.length() > 72) {
            throw new IllegalArgumentException("Password length must be at least 10 characters and no more than 72 characters");
        }

        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSymbol = false;
        for (char ch : password.toCharArray()) {
            if (Character.isUpperCase(ch)) hasUpper = true;
            if (Character.isLowerCase(ch)) hasLower = true;
            if (Character.isDigit(ch)) hasDigit = true;
            if (!Character.isLetterOrDigit(ch)) hasSymbol = true;
        }

        IntStream passwordChars = password.chars();
        if (!hasUpper || !hasLower || !hasDigit || !hasSymbol) {
            throw new IllegalArgumentException("Password must include upper, lower, digit, and symbol");
        }
    }
}
