package com.intuit.taxrefund.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class CookieService {
    private static final Logger log = LogManager.getLogger(CookieService.class);

    private final CookieProps props;

    public CookieService(CookieProps props) {
        this.props = props;
        log.info("cookie_service_initialized refreshName={} secure={} sameSite={}",
            props.refreshName(), props.secure(), props.sameSite());
    }

    public void setRefreshCookie(HttpServletResponse res, String refreshToken, long ageSeconds) {
        // DO NOT log refresh token value
        String refreshCookie = props.refreshName() + "=" + refreshToken
            + "; Path=/api/auth/refresh"
            + "; HttpOnly"
            + "; Max-Age=" + ageSeconds
            + (props.secure() ? "; Secure" : "")
            + "; SameSite=" + props.sameSite();

        res.addHeader(HttpHeaders.SET_COOKIE, refreshCookie);
        log.debug("refresh_cookie_set name={} ageSeconds={}", props.refreshName(), ageSeconds);
    }

    public void clearRefreshCookie(HttpServletResponse res) {
        this.setRefreshCookie(res, "", 0);
        log.debug("refresh_cookie_cleared name={}", props.refreshName());
    }

    public String refreshCookieName() {
        return this.props.refreshName();
    }
}