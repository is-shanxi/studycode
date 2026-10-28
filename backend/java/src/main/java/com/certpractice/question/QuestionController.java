package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public question library endpoints. Responses are safe views and never carry
 * correct-option markers, reference answers, aliases or explanations.
 */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionSearchService searchService;

    public QuestionController(final QuestionSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public PageResponse<QuestionDtos.QuestionSummary> search(
            @RequestParam(required = false) final UUID certificateId,
            @RequestParam(required = false) final UUID subjectId,
            @RequestParam(required = false) final List<UUID> chapterIds,
            @RequestParam(required = false) final List<UUID> tagIds,
            @RequestParam(required = false) final String keyword,
            @RequestParam(required = false) final List<String> difficulty,
            @RequestParam(required = false) final String type,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return searchService.searchPublic(
                certificateId,
                subjectId,
                chapterIds,
                tagIds,
                keyword,
                difficulty,
                type,
                new PageQuery(page, size));
    }

    @GetMapping("/{questionId}")
    public QuestionDtos.PublicQuestionView detail(@PathVariable final UUID questionId) {
        return searchService.getPublicQuestion(questionId);
    }
}
