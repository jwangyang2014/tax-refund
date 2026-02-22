package com.intuit.taxrefund.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {

        // Basic hardening headers
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        // CSP: demo-safe minimal (tune if your frontend loads assets from elsewhere)
        res.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'self'");

        // HSTS should only be enabled in HTTPS deployments;
        // Comment out for dev and demo
        // If running behind TLS in prod, turn this on:
        // res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        chain.doFilter(req, res);
    }
}