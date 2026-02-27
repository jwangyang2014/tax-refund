package com.intuit.taxrefund.auth;

import com.intuit.taxrefund.auth.jwt.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthPrincipalSupport {

    public JwtService.JwtPrincipal requirePrincipal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof JwtService.JwtPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }
}