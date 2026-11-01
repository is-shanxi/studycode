package com.certpractice.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.certpractice.support.P1TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Administrator catalog maintenance: certificates, subjects, chapters and tags
 * are created and edited through the admin API, audited, and only ACTIVE
 * entries are offered to the public catalog and tag pickers.
 */
class CatalogAdminIntegrationTest extends P1TestSupport {

    @Test
    void administratorMaintainsCatalogEntriesAndAuditRecords() throws Exception {
        String suffix = uniqueSuffix();
        String certificateCode = "cat-" + suffix;
        String subjectCode = "sub-" + suffix;
        String chapterCode = "ch-" + suffix;
        String tagName = "标签-" + suffix;

        MvcResult certificateResult = mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "  " + certificateCode + "  ",
                                "name", "测试证书",
                                "sortOrder", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(certificateCode.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        UUID certificateId = idOf(certificateResult);

        MvcResult subjectResult = mockMvc.perform(post("/api/v1/admin/catalog/subjects")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "certificateId", certificateId.toString(),
                                "code", subjectCode,
                                "name", "测试科目",
                                "sortOrder", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateId").value(certificateId.toString()))
                .andExpect(jsonPath("$.code").value(subjectCode.toUpperCase(Locale.ROOT)))
                .andReturn();
        UUID subjectId = idOf(subjectResult);

        MvcResult chapterResult = mockMvc.perform(post("/api/v1/admin/catalog/chapters")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectId", subjectId.toString(),
                                "code", chapterCode,
                                "name", "测试章节",
                                "sortOrder", 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(subjectId.toString()))
                .andExpect(jsonPath("$.code").value(chapterCode.toUpperCase(Locale.ROOT)))
                .andReturn();
        UUID chapterId = idOf(chapterResult);

        MvcResult tagResult = mockMvc.perform(post("/api/v1/admin/catalog/tags")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "  " + tagName + "  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(tagName))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        UUID tagId = idOf(tagResult);

        mockMvc.perform(get("/api/v1/admin/catalog/certificates").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(certificateId.toString())));

        mockMvc.perform(get("/api/v1/admin/catalog/subjects")
                        .queryParam("certificateId", certificateId.toString())
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(subjectId.toString())));

        mockMvc.perform(get("/api/v1/admin/catalog/chapters")
                        .queryParam("subjectId", subjectId.toString())
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(chapterId.toString())));

        mockMvc.perform(get("/api/v1/admin/catalog/tags").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(tagId.toString())));

        mockMvc.perform(put("/api/v1/admin/catalog/certificates/{certificateId}", certificateId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", certificateCode.toUpperCase(Locale.ROOT),
                                "name", "更新后的证书",
                                "status", "INACTIVE",
                                "sortOrder", 40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新后的证书"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(put("/api/v1/admin/catalog/tags/{tagId}", tagId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", tagName,
                                "status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .with(appUser(STUDENT_ID, "student01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificates[*].id", not(hasItem(certificateId.toString()))));

        mockMvc.perform(get("/api/v1/tags")
                        .with(appUser(STUDENT_ID, "student01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(tagId.toString()))));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .queryParam("action", "CATALOG_CERTIFICATE_CREATE")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetId").value(certificateId.toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("CERTIFICATE"));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .queryParam("action", "CATALOG_TAG_UPDATE")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetId").value(tagId.toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("TAG"));
    }

    @Test
    void ordinaryUsersCannotMaintainTheCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/certificates")
                        .with(appUser(STUDENT_ID, "student01")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(appUser(STUDENT_ID, "student01"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "NOPE",
                                "name", "Nope"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/catalog/tags")
                        .with(appUser(STUDENT_ID, "student01"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateCodesAndTagsAreRejected() throws Exception {
        String suffix = uniqueSuffix();
        String code = "dup-" + suffix;
        UUID certificateId = idOf(mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "name", "去重证书"))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", " " + code.toLowerCase(Locale.ROOT) + " ",
                                "name", "重复证书"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        UUID secondCertificateId = idOf(mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "other-" + suffix,
                                "name", "另一证书"))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(put("/api/v1/admin/catalog/certificates/{certificateId}", secondCertificateId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "name", "另一证书"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        String subjectCode = "subdup-" + suffix;
        mockMvc.perform(post("/api/v1/admin/catalog/subjects")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "certificateId", certificateId.toString(),
                                "code", subjectCode,
                                "name", "科目"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/catalog/subjects")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "certificateId", certificateId.toString(),
                                "code", subjectCode.toLowerCase(Locale.ROOT),
                                "name", "重复科目"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        String tagName = "重名标签-" + suffix;
        mockMvc.perform(post("/api/v1/admin/catalog/tags")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", tagName))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/catalog/tags")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "  " + tagName + "  "))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void validationAndMissingParentsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "VALID", "name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/catalog/certificates")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "bad code!", "name", "非法编码"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        UUID missingId = UUID.randomUUID();
        Map<String, Object> subject = new HashMap<>();
        subject.put("certificateId", missingId.toString());
        subject.put("code", "SUB-X");
        subject.put("name", "缺失父级科目");
        mockMvc.perform(post("/api/v1/admin/catalog/subjects")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subject)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Map<String, Object> chapter = new HashMap<>();
        chapter.put("subjectId", missingId.toString());
        chapter.put("code", "CH-X");
        chapter.put("name", "缺失父级章节");
        mockMvc.perform(post("/api/v1/admin/catalog/chapters")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chapter)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(put("/api/v1/admin/catalog/certificates/{certificateId}", missingId)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "MISSING", "name", "缺失"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private UUID idOf(final MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
