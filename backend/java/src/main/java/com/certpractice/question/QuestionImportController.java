package com.certpractice.question;

import java.util.UUID;

import com.certpractice.shared.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Batch import endpoints. Uploading only creates a preview; nothing is written
 * to the question bank until confirmation.
 */
@RestController
@RequestMapping("/api/v1/admin/question-imports")
public class QuestionImportController {

    private final QuestionImportService importService;
    private final CurrentUserService currentUserService;

    public QuestionImportController(
            final QuestionImportService importService,
            final CurrentUserService currentUserService) {
        this.importService = importService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDtos.ImportBatchView upload(@RequestParam("file") final MultipartFile file) {
        return importService.preview(currentUserService.requireUserId(), file);
    }

    @GetMapping("/{batchId}")
    public QuestionDtos.ImportBatchView get(@PathVariable final UUID batchId) {
        return importService.get(currentUserService.requireUserId(), batchId);
    }

    @PostMapping("/{batchId}/confirm")
    public QuestionDtos.ImportBatchView confirm(@PathVariable final UUID batchId) {
        return importService.confirm(currentUserService.requireUserId(), batchId);
    }

    @DeleteMapping("/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable final UUID batchId) {
        importService.cancel(currentUserService.requireUserId(), batchId);
    }
}
