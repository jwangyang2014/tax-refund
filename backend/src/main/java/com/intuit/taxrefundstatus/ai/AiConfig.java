package com.intuit.taxrefundstatus.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {
    private String provider;
    private OpenAi openAi = new OpenAi();

    public static class OpenAi {
        private String apiKey;
        private String model = "gpt-3.5-turbo"; // gpt-4o-mini

        public String getApiKey() {
            return apiKey;
        }
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        public String getModel() {
            return model;
        }
        public void setModel(String model) {
            this.model = model;
        }
    }
}
