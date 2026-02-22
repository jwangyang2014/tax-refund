package com.intuit.taxrefund.observability;

import com.intuit.taxrefund.auth.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {

        String requestId = req.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        ThreadContext.put("requestId", requestId);

        // Put userId into MDC if authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof JwtService.JwtPrincipal p) {
            ThreadContext.put("userId", String.valueOf(p.userId()));
        }

        // Return request id for clients / support
        res.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            chain.doFilter(req, res);
        } finally {
            ThreadContext.remove("requestId");
            ThreadContext.remove("userId");
        }
    }
}