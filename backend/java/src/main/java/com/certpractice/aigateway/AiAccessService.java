package com.certpractice.aigateway;

import java.util.UUID;

import com.certpractice.aigateway.dto.AiChatContext;
import com.certpractice.practice.PracticeAiContextService;
import org.springframework.stereotype.Service;

/**
 * Enforces the question-AI gate and builds the minimal model context. The
 * official explanation and reference answer are omitted unless the runtime
 * policy explicitly enables them.
 */
@Service
public class AiAccessService {

    private final PracticeAiContextService practiceContextService;
    private final AiConfigService configService;

    public AiAccessService(
            final PracticeAiContextService practiceContextService,
            final AiConfigService configService) {
        this.practiceContextService = practiceContextService;
        this.configService = configService;
    }

    public PracticeAiContextService.AiQuestionContext requireCompletedItem(
            final UUID userId,
            final UUID sessionItemId) {
        return practiceContextService.requireCompletedItem(userId, sessionItemId);
    }

    public AiChatContext contextFor(final AiConversation conversation) {
        if (conversation.getScope() == AiConversation.Scope.GENERAL) {
            return new AiChatContext("GENERAL", null, null, null, null, null, null);
        }
        PracticeAiContextService.AiQuestionContext question =
                practiceContextService.requireCompletedItem(
                        conversation.getUserId(), conversation.getSessionItemId());
        boolean sendOfficialAnswer = configService.current().sendOfficialAnswer();
        return new AiChatContext(
                "QUESTION",
                question.stem(),
                question.questionType(),
                question.userAnswer(),
                question.selfAssessment(),
                sendOfficialAnswer ? question.explanation() : null,
                sendOfficialAnswer ? question.referenceAnswer() : null);
    }
}
