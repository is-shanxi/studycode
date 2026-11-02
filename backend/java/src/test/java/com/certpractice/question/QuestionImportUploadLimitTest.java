package com.certpractice.question;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.Subject;
import com.certpractice.support.P1TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/**
 * Multipart uploads have an explicit, configurable size ceiling that is
 * enforced before CSV/XLSX parsing and reported with a stable error code.
 */
@TestPropertySource(properties = "app.question.import.max-file-bytes=2048")
class QuestionImportUploadLimitTest extends P1TestSupport {

    @Test
    void oversizedUploadsAreRejectedWithStableError() throws Exception {
        byte[] content = new byte[4096];
        Arrays.fill(content, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", MediaType.TEXT_PLAIN_VALUE, content);

        mockMvc.perform(multipart("/api/v1/admin/question-imports")
                        .file(file)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    void smallUploadsStillReachValidation() throws Exception {
        SubjectChapter catalog = createSubjectAndChapter();
        Subject subject = subjects.findById(catalog.subjectId()).orElseThrow();
        Chapter chapter = chapters.findById(catalog.chapterId()).orElseThrow();
        String csv = "type,subjectcode,chaptercode,stem,difficulty,options,correctoption\n"
                + "SINGLE_CHOICE," + subject.getCode() + "," + chapter.getCode()
                + ",Upload limit probe question,MEDIUM,A:one|B:two,A\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/question-imports")
                        .file(file)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREVIEW_READY"))
                .andExpect(jsonPath("$.validRows").value(1));
    }
}
