package com.intuit.taxrefund.profile.controller;

import com.intuit.taxrefund.auth.controller.dto.MeResponse;
import com.intuit.taxrefund.auth.jwt.JwtService;
import com.intuit.taxrefund.profile.dto.ChangePasswordRequest;
import com.intuit.taxrefund.profile.dto.ProfileResponse;
import com.intuit.taxrefund.profile.dto.UpdateProfileRequest;
import com.intuit.taxrefund.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        JwtService.JwtPrincipal principal = requirePrincipal(auth);
        return new MeResponse(principal.userId(), principal.email(), principal.role());
    }

    @GetMapping
    public ProfileResponse getProfile(Authentication auth) {
        JwtService.JwtPrincipal principal = requirePrincipal(auth);
        return profileService.getProfile(principal.userId());
    }

    @PutMapping
    public ProfileResponse updateProfile(
        Authentication auth,
        @Valid @RequestBody UpdateProfileRequest req
    ) {
        JwtService.JwtPrincipal principal = requirePrincipal(auth);
        return profileService.updateProfile(principal.userId(), req);
    }

    @PutMapping("/password")
    public void changePassword(
        Authentication auth,
        @Valid @RequestBody ChangePasswordRequest req
    ) {
        JwtService.JwtPrincipal principal = requirePrincipal(auth);
        profileService.changePassword(principal.userId(), req);
    }

    private JwtService.JwtPrincipal requirePrincipal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtService.JwtPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }
}