package com.certpractice.aigateway;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tutorial assistant endpoints. All access is scoped to the authenticated user;
 * question conversations are additionally gated by the practice item state.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiConversationService conversationService;
    private final AiQuotaService quotaService;
    private final CurrentUserService currentUserService;

    public AiController(
            final AiConversationService conversationService,
            final AiQuotaService quotaService,
            final CurrentUserService currentUserService) {
        this.conversationService = conversationService;
        this.quotaService = quotaService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/quota")
    public AiDtos.QuotaView quota(
            @RequestParam(required = false) final UUID conversationId) {
        return quotaService.quota(currentUserService.requireUserId(), conversationId);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AiDtos.ConversationView create(
            @Valid @RequestBody final AiDtos.CreateConversationRequest request) {
        return conversationService.create(currentUserService.requireUserId(), request);
    }

    @GetMapping("/conversations/{conversationId}")
    public AiDtos.ConversationView get(@PathVariable final UUID conversationId) {
        return conversationService.get(currentUserService.requireUserId(), conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable final UUID conversationId) {
        conversationService.clear(currentUserService.requireUserId(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AiDtos.SendMessageResponse send(
            @PathVariable final UUID conversationId,
            @Valid @RequestBody final AiDtos.SendMessageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) final String idempotencyKey) {
        return conversationService.send(
                currentUserService.requireUserId(), conversationId, request, idempotencyKey);
    }
}
