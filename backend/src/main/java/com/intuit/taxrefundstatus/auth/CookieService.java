package com.intuit.taxrefundstatus.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class CookieService {
    private final CookieProps props;

    public CookieService(CookieProps props) {
        this.props = props;
    }

    public void setRefreshCookie(HttpServletResponse res, String refreshToken, long ageSeconds) {
        String refreshCookie = props.refreshName() + "=" + refreshToken
                + "; Path=/api/auth/refresh"
                + "; HttpOnly"
                + "; Max-Age=" + ageSeconds
                + (props.secure() ? "; Secure" : "")
                + "; SameSite=" + props.sameSite();

        res.addHeader(HttpHeaders.SET_COOKIE, refreshCookie);
    }

    public void clearRefreshToken(HttpServletResponse res) {
        this.setRefreshCookie(res, "", 0);
    }

    public String refreshCookieName() {
        return this.props.refreshName();
    }
}
