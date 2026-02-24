package com.intuit.taxrefund.assistant.model;

import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse.Citation;
import com.intuit.taxrefund.assistant.policy.PolicySnippetRecord;
import com.intuit.taxrefund.assistant.policy.PolicySnippetRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class PolicySnippets {

    private final PolicySnippetRepository repository;
    private final Clock clock;

    public PolicySnippets(PolicySnippetRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Backward-compatible method used by AssistantService today.
     * Returns only docId + quote for LLM grounding / frontend citations.
     */
    public List<Citation> forStatus(String refundStatus) {
        return forStatusDetailed(refundStatus, "en-US").stream()
            .map(p -> new Citation(p.id(), p.quote()))
            .toList();
    }

    /**
     * Production-oriented method for richer UI / audit support.
     * Can later be used to return sourceUrl/title/version to frontend.
     */
    public List<PolicySnippetRecord> forStatusDetailed(String refundStatus, String locale) {
        String normalizedStatus = normalizeStatus(refundStatus);
        String normalizedLocale = normalizeLocale(locale);
        Instant now = Instant.now(clock);

        return repository.findCandidates(normalizedLocale).stream()
            .filter(p -> p.isActiveAt(now))
            .filter(p -> p.matchesLocale(normalizedLocale))
            .filter(p -> p.matchesStatus(normalizedStatus))
            .sorted(Comparator
                .comparingInt(PolicySnippetRecord::priority)
                .thenComparing(PolicySnippetRecord::id))
            .limit(5) // keep prompt payload tight
            .collect(Collectors.toList());
    }

    private static String normalizeStatus(String s) {
        if (s == null) return "UNKNOWN";
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLocale(String s) {
        if (s == null || s.isBlank()) return "en-US";
        return s.trim();
    }
}