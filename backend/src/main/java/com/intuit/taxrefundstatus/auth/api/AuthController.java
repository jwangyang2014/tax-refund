package com.intuit.taxrefundstatus.auth.api;

import com.intuit.taxrefundstatus.auth.CookieService;
import com.intuit.taxrefundstatus.auth.api.dto.LoginRequest;
import com.intuit.taxrefundstatus.auth.api.dto.MeResponse;
import com.intuit.taxrefundstatus.auth.api.dto.RegisterRequest;
import com.intuit.taxrefundstatus.auth.api.dto.TokenResponse;
import com.intuit.taxrefundstatus.auth.jwt.JwtService;
import com.intuit.taxrefundstatus.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        this.authService.register(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        AuthService.AuthTokens tokens = this.authService.login(req);

        this.cookieService.setRefreshCookie(res, tokens.refreshToken(), tokens.refreshMaxAge().toSeconds());
        return new TokenResponse(tokens.accessToken());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        String refreshCookie = readCookie(req, this.cookieService.refreshCookieName());

        if (refreshCookie == null) {
            throw new IllegalArgumentException("Missing refresh token");
        }

        AuthService.AuthTokens tokens = this.authService.refresh(refreshCookie);
        this.cookieService.setRefreshCookie(res, tokens.refreshToken(), tokens.refreshMaxAge().toSeconds());

        return new TokenResponse(tokens.accessToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
        String refreshCookie = readCookie(req, this.cookieService.refreshCookieName());
        this.authService.logout(refreshCookie);
        this.cookieService.clearRefreshToken(res);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        JwtService.JwtPrincipal principal = (JwtService.JwtPrincipal) auth.getPrincipal();
        return new MeResponse(principal.userId(), principal.email(), principal.role());
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();

        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
