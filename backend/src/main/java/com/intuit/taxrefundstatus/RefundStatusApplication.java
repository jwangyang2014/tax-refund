package com.intuit.taxrefundstatus;

import com.intuit.taxrefundstatus.auth.CookieProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CookieProps.class)
public class RefundStatusApplication {
    public static void main(String[] args) {
        SpringApplication.run(RefundStatusApplication.class, args);
    }
}
