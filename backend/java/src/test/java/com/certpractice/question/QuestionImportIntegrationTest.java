package com.certpractice.question;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class QuestionImportIntegrationTest extends P1TestSupport {

    @Test
    void csvPreviewReportsErrorsWithoutWritingAndConfirmCreatesDrafts() throws Exception {
        String invalidStem = "invalid import " + UUID.randomUUID();
        String invalidCsv = "type,subjectCode,chapterCode,stem,options,correctOption,fillAnswer,aliases,"
                + "explanation,difficulty,source,tags\n"
                + "SINGLE_CHOICE,NOPE,CH-01," + invalidStem + ",A:one|B:two,B,,,,MEDIUM,Test,基础\n";
        MvcResult invalidPreview = upload(invalidCsv.getBytes(StandardCharsets.UTF_8), "invalid.csv",
                "text/csv", 201);
        JsonNode invalidJson = readJson(invalidPreview);
        UUID invalidBatchId = UUID.fromString(invalidJson.path("batchId").asText());
        org.assertj.core.api.Assertions.assertThat(invalidJson.path("invalidRows").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(invalidJson.path("errors").get(0).path("code").asText())
                .isEqualTo("UNKNOWN_SUBJECT");

        mockMvc.perform(get("/api/v1/admin/questions")
                        .with(admin())
                        .param("keyword", invalidStem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(post("/api/v1/admin/question-imports/{id}/confirm", invalidBatchId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());

        String validStem = "valid import " + UUID.randomUUID();
        String validCsv = "type,subjectCode,chapterCode,stem,options,correctOption,fillAnswer,aliases,"
                + "explanation,difficulty,source,tags\n"
                + "SINGLE_CHOICE,SUB-DEMO-01,CH-01," + validStem
                + ",A:one|B:two,B,,,,MEDIUM,Test,基础\n";
        MvcResult preview = upload(validCsv.getBytes(StandardCharsets.UTF_8), "valid.csv",
                "text/csv", 201);
        JsonNode previewJson = readJson(preview);
        UUID batchId = UUID.fromString(previewJson.path("batchId").asText());
        org.assertj.core.api.Assertions.assertThat(previewJson.path("validRows").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(previewJson.path("invalidRows").asInt()).isZero();

        MvcResult confirmed = mockMvc.perform(
                        post("/api/v1/admin/question-imports/{id}/confirm", batchId)
                                .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(readJson(confirmed).hasNonNull("confirmedAt"))
                .isTrue();

        mockMvc.perform(get("/api/v1/admin/questions")
                        .with(admin())
                        .param("keyword", validStem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"));

        MvcResult duplicate = upload(validCsv.getBytes(StandardCharsets.UTF_8), "valid.csv",
                "text/csv", 201);
        org.assertj.core.api.Assertions.assertThat(readJson(duplicate).path("invalidRows").asInt())
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                        readJson(duplicate).path("errors").get(0).path("code").asText())
                .isEqualTo("DUPLICATE_QUESTION");
    }

    @Test
    void xlsxUploadIsParsedAndConfirmed() throws Exception {
        String stem = "xlsx import " + UUID.randomUUID();
        byte[] workbook = xlsx("SINGLE_BLANK,SUB-DEMO-01,CH-01," + stem
                + ",,,SYN,,Explanation,MEDIUM,Test,基础");
        MvcResult preview = upload(workbook, "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 201);
        JsonNode previewJson = readJson(preview);
        UUID batchId = UUID.fromString(previewJson.path("batchId").asText());
        org.assertj.core.api.Assertions.assertThat(previewJson.path("validRows").asInt()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/question-imports/{id}/confirm", batchId)
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/admin/questions")
                        .with(admin())
                        .param("keyword", stem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    private MvcResult upload(
            final byte[] content,
            final String fileName,
            final String contentType,
            final int expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, content);
        return mockMvc.perform(multipart("/api/v1/admin/question-imports")
                        .file(file)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private byte[] xlsx(final String dataRow) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("questions");
            Row header = sheet.createRow(0);
            String[] headers = {"type", "subjectCode", "chapterCode", "stem", "options",
                    "correctOption", "fillAnswer", "aliases", "explanation", "difficulty", "source", "tags"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            Row data = sheet.createRow(1);
            String[] values = dataRow.split(",", -1);
            for (int index = 0; index < values.length; index++) {
                data.createCell(index).setCellValue(values[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private JsonNode readJson(final MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
