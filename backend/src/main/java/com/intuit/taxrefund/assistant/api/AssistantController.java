package com.intuit.taxrefund.assistant.api;

import com.intuit.taxrefund.assistant.api.dto.AssistantChatRequest;
import com.intuit.taxrefund.assistant.api.dto.AssistantChatResponse;
import com.intuit.taxrefund.assistant.core.AssistantService;
import com.intuit.taxrefund.auth.jwt.JwtService;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LogManager.getLogger(AssistantController.class);

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(Authentication auth, @Valid @RequestBody AssistantChatRequest req) {
        JwtService.JwtPrincipal principal = (JwtService.JwtPrincipal) auth.getPrincipal();
        try {
            return assistantService.answer(principal, req.question());
        } catch (Exception e) {
            log.error("assistant_chat_failed userId={} err={}", principal.userId(), e.toString());
            return assistantService.answer(principal, "Show me my latest refund status and ETA.");
        }
    }
}