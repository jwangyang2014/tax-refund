package com.intuit.taxrefund.assistant.controller;

import com.intuit.taxrefund.assistant.controller.dto.AssistantChatRequest;
import com.intuit.taxrefund.assistant.controller.dto.AssistantChatResponse;
import com.intuit.taxrefund.assistant.service.AssistantService;
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
        return assistantService.answer(principal, req.question());
    }
}