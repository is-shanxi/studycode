package com.certpractice.community;

import java.util.Locale;
import java.util.UUID;

import com.certpractice.question.CorrectionReport;
import com.certpractice.question.CorrectionReportRepository;
import com.certpractice.question.Question;
import com.certpractice.question.QuestionRepository;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-submitted corrections for question stems, answers, explanations, tags or
 * other content. Corrections are handled by administrators and never modify a
 * published version in place.
 */
@Service
public class CorrectionReportService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final CorrectionReportRepository reports;
    private final ContentFilter contentFilter;

    public CorrectionReportService(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final CorrectionReportRepository reports,
            final ContentFilter contentFilter) {
        this.questions = questions;
        this.versions = versions;
        this.reports = reports;
        this.contentFilter = contentFilter;
    }

    @Transactional
    public CommunityDtos.ReportCreated create(
            final UUID userId,
            final CommunityDtos.CorrectionReportRequest request) {
        Question question = questions.findById(request.questionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found"));
        Guard.require(question.getStatus() == Question.Status.PUBLISHED,
                ErrorCode.NOT_FOUND, "Question not found");
        versions.findByIdAndQuestionId(request.questionVersionId(), request.questionId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "Question version does not belong to the question"));

        CorrectionReport report = new CorrectionReport();
        report.setReporterUserId(userId);
        report.setQuestionId(request.questionId());
        report.setQuestionVersionId(request.questionVersionId());
        report.setTarget(parseTarget(request.target()));
        report.setDescription(contentFilter.sanitizeRequired(request.description(), "description"));
        report.setStatus(CorrectionReport.Status.OPEN);
        reports.save(report);
        return new CommunityDtos.ReportCreated(report.getId(), report.getStatus().name());
    }

    private CorrectionReport.Target parseTarget(final String target) {
        try {
            return CorrectionReport.Target.valueOf(target.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "target must be one of STEM, ANSWER, EXPLANATION, TAG, OTHER");
        }
    }
}
