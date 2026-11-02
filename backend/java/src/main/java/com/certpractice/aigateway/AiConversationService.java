package com.certpractice.aigateway;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.certpractice.aigateway.dto.AiChatLimits;
import com.certpractice.aigateway.dto.AiChatMessage;
import com.certpractice.aigateway.dto.AiChatRequest;
import com.certpractice.aigateway.dto.AiChatResponse;
import com.certpractice.aigateway.dto.AiUsage;
import com.certpractice.identity.IdempotencyRecord;
import com.certpractice.practice.PracticeAiContextService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.idempotency.IdempotencyService;
import com.certpractice.shared.observability.TraceContext;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI conversations and message exchange. Quota reservation and the user message
 * commit before the Python call; the assistant message and settlement commit in
 * a separate transaction afterwards. Provider calls never run inside a database
 * transaction.
 */
@Service
public class AiConversationService {

    private static final String CHAT_OPERATION = "AI_CHAT_MESSAGE";
    private static final int MAX_OUTPUT_TOKENS = 1200;
    private static final int LOCK_STRIPES = 64;

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiAccessService accessService;
    private final AiQuotaService quotaService;
    private final AiConfigService configService;
    private final AiServiceClient aiServiceClient;
    private final AiProperties aiProperties;
    private final IdempotencyService idempotency;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Object[] locks = new Object[LOCK_STRIPES];

    public AiConversationService(
            final AiConversationRepository conversations,
            final AiMessageRepository messages,
            final AiAccessService accessService,
            final AiQuotaService quotaService,
            final AiConfigService configService,
            final AiServiceClient aiServiceClient,
            final AiProperties aiProperties,
            final IdempotencyService idempotency,
            final Clock clock,
            final PlatformTransactionManager transactionManager) {
        this.conversations = conversations;
        this.messages = messages;
        this.accessService = accessService;
        this.quotaService = quotaService;
        this.configService = configService;
        this.aiServiceClient = aiServiceClient;
        this.aiProperties = aiProperties;
        this.idempotency = idempotency;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
    }

    @Transactional
    public AiDtos.ConversationView create(
            final UUID userId,
            final AiDtos.CreateConversationRequest request) {
        AiConversation.Scope scope = parseScope(request.scope());
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setScope(scope);
        conversation.setStatus(AiConversation.Status.ACTIVE);
        if (scope == AiConversation.Scope.QUESTION) {
            Guard.require(request.sessionItemId() != null, ErrorCode.VALIDATION_FAILED,
                    "sessionItemId is required for question conversations");
            PracticeAiContextService.AiQuestionContext question =
                    accessService.requireCompletedItem(userId, request.sessionItemId());
            conversation.setQuestionId(question.questionId());
            conversation.setQuestionVersionId(question.questionVersionId());
            conversation.setSessionItemId(question.sessionItemId());
        } else {
            Guard.require(request.sessionItemId() == null, ErrorCode.VALIDATION_FAILED,
                    "General conversations must not carry a session item");
        }
        conversations.save(conversation);
        return toView(conversation, List.of());
    }

    @Transactional(readOnly = true)
    public AiDtos.ConversationView get(final UUID userId, final UUID conversationId) {
        AiConversation conversation = requireOwned(userId, conversationId);
        return toView(conversation, messages.findByConversationIdOrderByCreatedAtAsc(conversationId));
    }

    @Transactional
    public void clear(final UUID userId, final UUID conversationId) {
        AiConversation conversation = requireOwned(userId, conversationId);
        messages.deleteByConversationId(conversationId);
        conversation.setStatus(AiConversation.Status.CLEARED);
        conversations.save(conversation);
    }

    public AiDtos.SendMessageResponse send(
            final UUID userId,
            final UUID conversationId,
            final AiDtos.SendMessageRequest request,
            final String idempotencyKey) {
        AiConversation conversation = requireOwned(userId, conversationId);
        AiConfigService.AiSettings settings = configService.current();
        String content = request.content() == null ? "" : request.content().trim();
        Guard.require(!content.isEmpty(), ErrorCode.VALIDATION_FAILED, "content is required");
        Guard.require(content.length() <= settings.maxInputChars(), ErrorCode.VALIDATION_FAILED,
                "content exceeds the configured maximum length");

        Map<String, Object> payload = Map.of(
                "conversationId", conversationId.toString(),
                "content", content);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return exchange(conversation, content, null, payload, settings);
        }
        synchronized (lockFor(userId, idempotencyKey)) {
            Optional<IdempotencyRecord> existing = idempotency.find(
                    userId, CHAT_OPERATION, idempotencyKey, payload);
            if (existing.isPresent()) {
                return idempotency.replay(existing.get(), AiDtos.SendMessageResponse.class)
                        .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                                "Stored AI response is unreadable"));
            }
            return exchange(conversation, content, idempotencyKey, payload, settings);
        }
    }

    private AiDtos.SendMessageResponse exchange(
            final AiConversation conversation,
            final String content,
            final String idempotencyKey,
            final Map<String, Object> payload,
            final AiConfigService.AiSettings settings) {
        PreparedChat prepared = transactionTemplate.execute(status ->
                prepare(conversation.getId(), conversation.getUserId(), content,
                        idempotencyKey, settings));

        AiChatResponse response;
        try {
            response = aiServiceClient.chat(prepared.request(), TraceContext.currentTraceId());
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new ApiException(ErrorCode.AI_UNAVAILABLE,
                        "AI service returned an empty response");
            }
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(prepared));
            throw exception;
        }

        return transactionTemplate.execute(status ->
                complete(prepared, response, idempotencyKey, payload, conversation.getUserId()));
    }

    private PreparedChat prepare(
            final UUID conversationId,
            final UUID userId,
            final String content,
            final String idempotencyKey,
            final AiConfigService.AiSettings settings) {
        AiQuotaService.Reservation reservation =
                quotaService.reserve(userId, conversationId, idempotencyKey, settings);

        AiConversation conversation = conversations.findByIdAndUserIdForUpdate(conversationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Conversation not found"));
        Guard.require(conversation.getStatus() == AiConversation.Status.ACTIVE,
                ErrorCode.STATE_CONFLICT, "Conversation is not active");
        Guard.require(quotaService.turnsUsed(conversationId) < settings.sessionTurnLimit(),
                ErrorCode.RATE_LIMITED, "Conversation turn limit has been reached");

        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(AiMessage.Role.USER);
        userMessage.setContent(content);
        messages.saveAndFlush(userMessage);

        List<AiChatMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(message -> new AiChatMessage(
                        message.getRole().name().toLowerCase(Locale.ROOT), message.getContent()))
                .toList();
        AiChatRequest request = new AiChatRequest(
                reservation.requestId(),
                aiProperties.provider(),
                aiProperties.model(),
                history,
                accessService.contextFor(conversation),
                new AiChatLimits(MAX_OUTPUT_TOKENS, aiProperties.requestTimeout().toMillis()));
        return new PreparedChat(conversationId, userMessage.getId(), reservation.requestId(), request);
    }

    private AiDtos.SendMessageResponse complete(
            final PreparedChat prepared,
            final AiChatResponse response,
            final String idempotencyKey,
            final Map<String, Object> payload,
            final UUID userId) {
        AiConversation conversation = conversations.findById(prepared.conversationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Conversation not found"));
        AiUsage usage = response.usage();
        int inputTokens = usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens();
        int outputTokens = usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens();

        AiMessage assistant = new AiMessage();
        assistant.setConversationId(prepared.conversationId());
        assistant.setRole(AiMessage.Role.ASSISTANT);
        assistant.setContent(response.content());
        assistant.setProvider(aiProperties.provider());
        assistant.setModelName(aiProperties.model());
        assistant.setInputTokens(inputTokens);
        assistant.setOutputTokens(outputTokens);
        assistant.setRequestId(prepared.requestId());
        messages.saveAndFlush(assistant);

        conversation.setLastMessageAt(clock.instant());
        conversations.save(conversation);
        quotaService.settleSuccess(
                prepared.requestId(), inputTokens, outputTokens, response.providerRequestId());

        AiMessage userMessage = messages.findById(prepared.userMessageId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "User message is missing"));
        AiDtos.SendMessageResponse result = new AiDtos.SendMessageResponse(
                prepared.conversationId(),
                prepared.requestId(),
                toMessageView(userMessage),
                toMessageView(assistant),
                new AiDtos.UsageView(inputTokens, outputTokens),
                quotaService.quota(userId, prepared.conversationId()));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.store(userId, CHAT_OPERATION, idempotencyKey, payload, 200, result);
        }
        return result;
    }

    private void fail(final PreparedChat prepared) {
        messages.deleteById(prepared.userMessageId());
        quotaService.refund(prepared.requestId(), ErrorCode.AI_UNAVAILABLE.name());
    }

    private AiConversation requireOwned(final UUID userId, final UUID conversationId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Conversation not found"));
    }

    private AiConversation.Scope parseScope(final String value) {
        try {
            return AiConversation.Scope.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "scope must be GENERAL or QUESTION", exception);
        }
    }

    private Object lockFor(final UUID userId, final String key) {
        return locks[Math.floorMod((userId + ":" + key).hashCode(), locks.length)];
    }

    private AiDtos.ConversationView toView(
            final AiConversation conversation,
            final List<AiMessage> storedMessages) {
        return new AiDtos.ConversationView(
                conversation.getId(),
                conversation.getScope().name(),
                conversation.getStatus().name(),
                conversation.getQuestionId(),
                conversation.getQuestionVersionId(),
                conversation.getSessionItemId(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                storedMessages.stream().map(this::toMessageView).toList());
    }

    private AiDtos.MessageView toMessageView(final AiMessage message) {
        return new AiDtos.MessageView(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getProvider(),
                message.getModelName(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt());
    }

    private record PreparedChat(
            UUID conversationId,
            UUID userMessageId,
            UUID requestId,
            AiChatRequest request) {
    }
}
