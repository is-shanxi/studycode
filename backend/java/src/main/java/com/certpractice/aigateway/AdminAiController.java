package com.certpractice.aigateway;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator AI generation and policy endpoints under /api/v1/admin.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAiController {

    private final AiGenerationService generationService;
    private final AiCandidateService candidateService;
    private final AiConfigService configService;
    private final CurrentUserService currentUserService;

    public AdminAiController(
            final AiGenerationService generationService,
            final AiCandidateService candidateService,
            final AiConfigService configService,
            final CurrentUserService currentUserService) {
        this.generationService = generationService;
        this.candidateService = candidateService;
        this.configService = configService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/configs")
    public AiAdminDtos.ConfigView config(
            @RequestParam(defaultValue = AiConfigService.AI_SETTINGS_KEY) final String key) {
        currentUserService.requireAdmin();
        return configService.latest(key);
    }

    @PutMapping("/configs")
    public AiAdminDtos.ConfigView updateConfig(
            @Valid @RequestBody final AiAdminDtos.UpdateConfigRequest request) {
        currentUserService.requireAdmin();
        return configService.update(
                currentUserService.requireUserId(), request.key(), request.value());
    }

    @GetMapping("/ai/generation-batches")
    public PageResponse<AiAdminDtos.GenerationBatchView> listBatches(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        currentUserService.requireAdmin();
        return generationService.list(new PageQuery(page, size));
    }

    @PostMapping("/ai/generation-batches")
    @ResponseStatus(HttpStatus.CREATED)
    public AiAdminDtos.GenerationBatchView createBatch(
            @Valid @RequestBody final AiAdminDtos.GenerationBatchRequest request) {
        currentUserService.requireAdmin();
        return generationService.create(currentUserService.requireUserId(), request);
    }

    @GetMapping("/ai/generation-batches/{batchId}")
    public AiAdminDtos.GenerationBatchView batch(@PathVariable final UUID batchId) {
        currentUserService.requireAdmin();
        return generationService.get(batchId);
    }

    @PostMapping("/ai/generation-candidates/{candidateId}/review")
    public AiAdminDtos.GenerationCandidateView review(
            @PathVariable final UUID candidateId,
            @Valid @RequestBody final AiAdminDtos.CandidateReviewRequest request) {
        currentUserService.requireAdmin();
        return candidateService.review(
                currentUserService.requireUserId(),
                candidateId,
                request.decision(),
                request.comment());
    }
}
