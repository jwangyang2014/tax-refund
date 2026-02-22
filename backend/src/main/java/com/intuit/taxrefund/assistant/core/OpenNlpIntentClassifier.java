package com.intuit.taxrefund.assistant.core;

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Locale;

@Component
@Primary
public class OpenNlpIntentClassifier implements IntentClassifier {
    private static final Logger log = LogManager.getLogger(OpenNlpIntentClassifier.class);

    private final KeywordIntentClassifier fallback;

    @Value("${app.assistant.intentClassifier:opennlp}")
    private String mode;

    @Value("${app.assistant.intentMinConfidence:0.55}")
    private double minConfidence;

    @Value("classpath:/nlp/intent-model.bin")
    private Resource modelResource;

    private volatile DocumentCategorizerME categorizer;

    public OpenNlpIntentClassifier(KeywordIntentClassifier fallback) {
        this.fallback = fallback;
    }

    @PostConstruct
    void init() {
        if (!"opennlp".equalsIgnoreCase(mode)) {
            log.info("OpenNLP classifier disabled (mode={}), using keyword only", mode);
            return;
        }

        try (InputStream in = modelResource.getInputStream()) {
            DoccatModel model = new DoccatModel(in);
            this.categorizer = new DocumentCategorizerME(model);
            log.info("OpenNLP intent model loaded");
        } catch (Exception e) {
            log.warn("Failed to load OpenNLP model, falling back to keyword. err={}", e.toString());
            this.categorizer = null;
        }
    }

    @Override
    public IntentResult classify(String text) {
        if (!"opennlp".equalsIgnoreCase(mode) || categorizer == null) {
            return fallback.classify(text);
        }

        String normalized = normalize(text);
        String[] tokens = normalized.split("\\s+");

        double[] outcomes = categorizer.categorize(tokens);
        String bestCategory = categorizer.getBestCategory(outcomes);
        double confidence = max(outcomes);

        AssistantIntent intent = map(bestCategory);

        // Fallback when model is uncertain
        if (confidence < minConfidence) {
            IntentResult fb = fallback.classify(text);
            return new IntentResult(fb.intent(), Math.max(fb.confidence(), confidence), "opennlp->keyword");
        }

        return new IntentResult(intent, confidence, "opennlp");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static double max(double[] a) {
        double m = 0.0;
        for (double v : a) m = Math.max(m, v);
        return m;
    }

    private static AssistantIntent map(String category) {
        return switch (category) {
            case "REFUND_STATUS" -> AssistantIntent.REFUND_STATUS;
            case "REFUND_ETA" -> AssistantIntent.REFUND_ETA;
            case "WHY_DELAYED" -> AssistantIntent.WHY_DELAYED;
            case "NEXT_STEPS" -> AssistantIntent.NEXT_STEPS;
            default -> AssistantIntent.UNKNOWN;
        };
    }
}