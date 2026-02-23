package com.intuit.taxrefund.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml")
public record MlProps(String baseUrl) {}