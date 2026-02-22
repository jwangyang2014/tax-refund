package com.intuit.taxrefund.ratelimit;

import com.intuit.taxrefund.auth.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger(RateLimitFilter.class);

    private final RedisRateLimiter limiter;
    private final RateLimitProps props;

    public RateLimitFilter(RedisRateLimiter limiter, RateLimitProps props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {

        if (!props.enabled()) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        RateLimitProps.Policy policy = null;
        if ("GET".equals(method) && "/api/refund/latest".equals(path)) policy = props.refundLatest();
        if ("POST".equals(method) && "/api/assistant/chat".equals(path)) policy = props.assistantChat();

        if (policy == null) {
            chain.doFilter(req, res);
            return;
        }

        String principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof JwtService.JwtPrincipal p) {
            principal = "u:" + p.userId();
        } else {
            principal = "ip:" + req.getRemoteAddr();
        }

        String key = "rl:" + principal + ":" + method + ":" + path;

        var r = limiter.tryConsume(key, policy.capacity(), policy.refillPerMinute(), 1);
        if (!r.allowed()) {
            log.warn("rate_limited principal={} method={} path={}", principal, method, path);

            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", "10");
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"rate_limited\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}