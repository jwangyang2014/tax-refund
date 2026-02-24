package com.intuit.taxrefund.profile;

import com.intuit.taxrefund.auth.controller.dto.MeResponse;
import com.intuit.taxrefund.auth.jwt.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtService.JwtPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return new MeResponse(principal.userId(), principal.email(), principal.role());
    }
}