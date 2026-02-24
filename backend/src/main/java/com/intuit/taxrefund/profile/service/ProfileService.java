package com.intuit.taxrefund.profile.service;

import com.intuit.taxrefund.auth.model.AppUser;
import com.intuit.taxrefund.auth.repository.UserRepository;
import com.intuit.taxrefund.profile.dto.ProfileResponse;
import com.intuit.taxrefund.profile.dto.UpdateProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

        // Defensive trimming + normalization
        user.setFirstName(safeTrim(req.firstName()));
        user.setLastName(safeTrim(req.lastName()));
        user.setCity(safeTrim(req.city()));
        user.setState(safeTrim(req.state()).toUpperCase());

        // Optional fields
        user.setAddress(toOptional(req.address()));
        user.setPhone(toOptional(req.phone()));

        userRepository.save(user);
        return toProfileResponse(user);
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String toOptional(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
}