package com.certpractice.practice;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user records, favorites and active wrong questions. All operations are
 * scoped to the authenticated owner.
 */
@RestController
@RequestMapping("/api/v1/me")
public class LearningHistoryController {

    private final PracticeRecordService recordService;
    private final FavoriteService favoriteService;
    private final WrongQuestionService wrongQuestionService;
    private final PracticeDtoMapper mapper;
    private final CurrentUserService currentUserService;

    public LearningHistoryController(
            final PracticeRecordService recordService,
            final FavoriteService favoriteService,
            final WrongQuestionService wrongQuestionService,
            final PracticeDtoMapper mapper,
            final CurrentUserService currentUserService) {
        this.recordService = recordService;
        this.favoriteService = favoriteService;
        this.wrongQuestionService = wrongQuestionService;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/records")
    public PageResponse<PracticeDtos.RecordSummary> records(
            @RequestParam(required = false) final String mode,
            @RequestParam(required = false) final String status,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return recordService.list(
                currentUserService.requireUserId(), mode, status, new PageQuery(page, size));
    }

    @GetMapping("/records/{sessionId}")
    public PracticeDtos.ResultView recordDetail(@PathVariable final UUID sessionId) {
        return recordService.detail(currentUserService.requireUserId(), sessionId);
    }

    @GetMapping("/favorites")
    public List<PracticeDtos.FavoriteView> favorites() {
        return favoriteService.list(currentUserService.requireUserId());
    }

    @PutMapping("/favorites/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@PathVariable final UUID questionId) {
        favoriteService.add(currentUserService.requireUserId(), questionId);
    }

    @DeleteMapping("/favorites/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable final UUID questionId) {
        favoriteService.remove(currentUserService.requireUserId(), questionId);
    }

    @GetMapping("/wrong-questions")
    public List<PracticeDtos.WrongQuestionView> wrongQuestions() {
        return wrongQuestionService.listActive(currentUserService.requireUserId())
                .stream()
                .map(mapper::toWrongQuestionView)
                .filter(Objects::nonNull)
                .toList();
    }

    @DeleteMapping("/wrong-questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWrongQuestion(@PathVariable final UUID questionId) {
        wrongQuestionService.remove(currentUserService.requireUserId(), questionId);
    }
}
