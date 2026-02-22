package com.intuit.taxrefund.auth.api;

import com.intuit.taxrefund.auth.CookieService;
import com.intuit.taxrefund.auth.api.dto.LoginRequest;
import com.intuit.taxrefund.auth.api.dto.MeResponse;
import com.intuit.taxrefund.auth.api.dto.RegisterRequest;
import com.intuit.taxrefund.auth.api.dto.TokenResponse;
import com.intuit.taxrefund.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            var user = this.authService.register(req);
            log.info("user_registered userId={} email={}", user.getId(), user.getEmail());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("register_failed email={} err={}", safeEmail(req.email()), e.toString());
            throw e;
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        try {
            AuthService.AuthTokens tokens = this.authService.login(req);
            this.cookieService.setRefreshCookie(res, tokens.refreshToken(), tokens.refreshMaxAge().toSeconds());

            log.info("login_success email={}", safeEmail(req.email()));
            return new TokenResponse(tokens.accessToken());
        } catch (Exception e) {
            log.warn("login_failed email={} err={}", safeEmail(req.email()), e.toString());
            throw e;
        }
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        String refreshCookie = readCookie(req, this.cookieService.refreshCookieName());
        if (refreshCookie == null) {
            log.warn("refresh_missing_cookie");
            throw new IllegalArgumentException("Missing refresh token");
        }

        try {
            AuthService.AuthTokens tokens = this.authService.refresh(refreshCookie);
            this.cookieService.setRefreshCookie(res, tokens.refreshToken(), tokens.refreshMaxAge().toSeconds());
            log.info("refresh_success");
            return new TokenResponse(tokens.accessToken());
        } catch (Exception e) {
            log.warn("refresh_failed err={}", e.toString());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
        String refreshCookie = readCookie(req, this.cookieService.refreshCookieName());
        try {
            this.authService.logout(refreshCookie);
        } catch (Exception e) {
            log.warn("logout_failed err={}", e.toString());
        }
        this.cookieService.clearRefreshCookie(res);
        log.info("logout_success");
        return ResponseEntity.ok().build();
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) return cookie.getValue();
        }
        return null;
    }

    private static String safeEmail(String email) {
        if (email == null) return "null";
        String e = email.trim().toLowerCase();
        // avoid dumping full email in prod; for demo this is OK-ish but still mask a bit
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        return e.substring(0, 1) + "***" + e.substring(at);
    }
}