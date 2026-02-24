package com.intuit.taxrefund.assistant.policy;

import java.util.List;

/**
 * On production, this can be document DB like MongoDB or Cosmos DB.
 * For demo purpose, we can just hardcode some snippets in memory.
 */
public interface PolicySnippetRepository {
    List<PolicySnippetRecord> findCandidates(String locale);
}