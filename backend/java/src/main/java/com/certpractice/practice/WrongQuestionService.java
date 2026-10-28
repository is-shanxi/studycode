package com.certpractice.practice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active wrong-question state plus append-only failure events. Removing a wrong
 * question never deletes the original answer or event history, and a later
 * failure reactivates the same stable question identity.
 */
@Service
public class WrongQuestionService {

    private final WrongQuestionRepository wrongQuestions;
    private final WrongQuestionEventRepository events;

    public WrongQuestionService(
            final WrongQuestionRepository wrongQuestions,
            final WrongQuestionEventRepository events) {
        this.wrongQuestions = wrongQuestions;
        this.events = events;
    }

    @Transactional
    public void recordFailure(final UUID userId, final UUID questionId, final UUID sessionItemId) {
        Optional<WrongQuestion> active = wrongQuestions
                .findByUserIdAndQuestionIdAndActiveTrue(userId, questionId);
        if (active.isPresent()) {
            WrongQuestion wrong = active.get();
            wrong.setLastFailedAt(Instant.now());
            wrongQuestions.save(wrong);
            return;
        }

        Optional<WrongQuestion> existing = wrongQuestions.findByUserIdAndQuestionId(userId, questionId);
        if (existing.isPresent()) {
            WrongQuestion wrong = existing.get();
            wrong.setActive(true);
            wrong.setRemovedAt(null);
            wrong.setLastFailedAt(Instant.now());
            wrongQuestions.save(wrong);
            appendEvent(wrong.getId(), sessionItemId, WrongQuestionEvent.EventType.REACTIVATED);
            return;
        }

        WrongQuestion wrong = new WrongQuestion();
        wrong.setUserId(userId);
        wrong.setQuestionId(questionId);
        wrong.setActive(true);
        wrong.setLastFailedAt(Instant.now());
        wrongQuestions.save(wrong);
        appendEvent(wrong.getId(), sessionItemId, WrongQuestionEvent.EventType.ADDED);
    }

    @Transactional
    public void remove(final UUID userId, final UUID questionId) {
        Optional<WrongQuestion> active = wrongQuestions
                .findByUserIdAndQuestionIdAndActiveTrue(userId, questionId);
        if (active.isEmpty()) {
            return;
        }
        WrongQuestion wrong = active.get();
        wrong.setActive(false);
        wrong.setRemovedAt(Instant.now());
        wrongQuestions.save(wrong);
        appendEvent(wrong.getId(), null, WrongQuestionEvent.EventType.REMOVED);
    }

    @Transactional(readOnly = true)
    public List<WrongQuestion> listActive(final UUID userId) {
        return wrongQuestions.findByUserIdAndActiveTrueOrderByLastFailedAtDesc(userId);
    }

    private void appendEvent(
            final UUID wrongQuestionId,
            final UUID sessionItemId,
            final WrongQuestionEvent.EventType type) {
        WrongQuestionEvent event = new WrongQuestionEvent();
        event.setWrongQuestionId(wrongQuestionId);
        event.setSessionItemId(sessionItemId);
        event.setEventType(type);
        events.save(event);
    }
}
