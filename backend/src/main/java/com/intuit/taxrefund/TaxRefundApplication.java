package com.intuit.taxrefund;

import com.intuit.taxrefund.assistant.infra.AssistantProps;
import com.intuit.taxrefund.llm.AiProps;
import com.intuit.taxrefund.shared.config.DemoProps;
import com.intuit.taxrefund.ml.MlProps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.intuit.taxrefund.auth.CookieProps;
import com.intuit.taxrefund.shared.ratelimit.RateLimitProps;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
    CookieProps.class, RateLimitProps.class, MlProps.class, AiProps.class,
    DemoProps.class, AssistantProps.class
})
@EnableScheduling
public class TaxRefundApplication {

    private static final Logger log = LogManager.getLogger(TaxRefundApplication.class);

    public static void main(String[] args) {
        log.info("tax_refund_app_starting");
        SpringApplication.run(TaxRefundApplication.class, args);
    }
}