package com.certpractice.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.certpractice.question.Question;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersion;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent favorites keyed by stable question identity.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;

    public FavoriteService(
            final FavoriteRepository favorites,
            final QuestionRepository questions,
            final QuestionVersionRepository versions) {
        this.favorites = favorites;
        this.questions = questions;
        this.versions = versions;
    }

    @Transactional
    public void add(final UUID userId, final UUID questionId) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
        Guard.require(question.getStatus() == Question.Status.PUBLISHED
                        && question.getPublishedVersionId() != null,
                ErrorCode.NOT_FOUND, "Question not found");
        if (favorites.existsByIdUserIdAndIdQuestionId(userId, questionId)) {
            return;
        }
        Favorite favorite = new Favorite();
        FavoriteId id = new FavoriteId();
        id.setUserId(userId);
        id.setQuestionId(questionId);
        favorite.setId(id);
        favorites.save(favorite);
    }

    @Transactional
    public void remove(final UUID userId, final UUID questionId) {
        favorites.findByIdUserIdAndIdQuestionId(userId, questionId)
                .ifPresent(favorites::delete);
    }

    @Transactional(readOnly = true)
    public List<PracticeDtos.FavoriteView> list(final UUID userId) {
        List<PracticeDtos.FavoriteView> views = new ArrayList<>();
        for (Favorite favorite : favorites.findByIdUserIdOrderByCreatedAtDesc(userId)) {
            Question question = questions.findById(favorite.getId().getQuestionId()).orElse(null);
            if (question == null) {
                continue;
            }
            QuestionVersion version = publishedVersion(question);
            if (version == null) {
                continue;
            }
            views.add(new PracticeDtos.FavoriteView(
                    question.getId(),
                    version.getId(),
                    question.getType().name(),
                    version.getStem(),
                    version.getDifficulty().name(),
                    question.getStatus().name(),
                    favorite.getCreatedAt()));
        }
        return views;
    }

    private QuestionVersion publishedVersion(final Question question) {
        if (question.getStatus() != Question.Status.PUBLISHED
                || question.getPublishedVersionId() == null) {
            return null;
        }
        return versions.findById(question.getPublishedVersionId()).orElse(null);
    }
}
