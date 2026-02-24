package com.intuit.taxrefund.profile.service;

import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.repository.UserRepository;
import com.intuit.taxrefund.auth.service.PasswordPolicy;
import com.intuit.taxrefund.profile.dto.ChangePasswordRequest;
import com.intuit.taxrefund.profile.dto.ProfileResponse;
import com.intuit.taxrefund.profile.dto.UpdateProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public ProfileService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        PasswordPolicy passwordPolicy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setFirstName(safeTrim(req.firstName()));
        user.setLastName(safeTrim(req.lastName()));
        user.setCity(safeTrim(req.city()));
        user.setState(safeTrim(req.state()).toUpperCase());

        user.setAddress(toOptional(req.address()));
        user.setPhone(toOptional(req.phone()));

        userRepository.save(user);
        return toProfileResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currentPassword = safeTrim(req.currentPassword());
        String newPassword = safeTrim(req.newPassword());

        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current and new password are required");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        // ✅ Reuse shared backend policy (same as registration)
        try {
            passwordPolicy.validate(newPassword);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "New password must be different from current password"
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private static ProfileResponse toProfileResponse(AppUser user) {
        return new ProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getFirstName(),
            user.getLastName(),
            user.getAddress(),
            user.getCity(),
            user.getState(),
            user.getPhone()
        );
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String toOptional(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}