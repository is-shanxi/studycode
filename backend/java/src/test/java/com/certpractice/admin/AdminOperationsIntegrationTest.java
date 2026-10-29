package com.certpractice.admin;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditLogRepository;
import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.identity.UserRole;
import com.certpractice.identity.UserRoleId;
import com.certpractice.identity.UserRoleRepository;
import com.certpractice.practice.PracticeSession;
import com.certpractice.practice.SessionItem;
import com.certpractice.shared.security.Role;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.support.P1TestSupport;
import com.certpractice.support.P2TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

class AdminOperationsIntegrationTest extends P2TestSupport {

    private static final String ORIGINAL_PASSWORD = "Original@123";

    @Autowired
    private AppUserRepository users;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminAuditLogRepository auditLogs;

    @Test
    void disablingAUserInvalidatesTheSessionAndEnablingRestoresAccess() throws Exception {
        String username = uniqueUsername("disabled");
        UUID userId = createUser(username, ORIGINAL_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").with(appUser(userId, username)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users")
                        .queryParam("keyword", username)
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/disable", userId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/me").with(appUser(userId, username)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", ORIGINAL_PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/enable", userId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/auth/me").with(appUser(userId, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .queryParam("action", "USER_DISABLE")
                        .queryParam("outcome", "SUCCESS")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].action", everyItem(is("USER_DISABLE"))))
                .andExpect(jsonPath("$.items[0].targetId").value(userId.toString()))
                .andExpect(jsonPath("$.items[0].summary").value(
                        org.hamcrest.Matchers.containsString(username)));
    }

    @Test
    void passwordResetReturnsAOneTimeSecretAndInvalidatesTheOldPassword() throws Exception {
        String username = uniqueUsername("reset");
        UUID userId = createUser(username, ORIGINAL_PASSWORD);
        int auditCountBefore = auditLogs.findAll().size();

        MvcResult reset = mockMvc.perform(post("/api/v1/admin/users/{userId}/reset-password", userId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();
        String temporaryPassword = readJson(reset).get("temporaryPassword").asText();

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", ORIGINAL_PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        AdminAuditLog audit = auditLogs.findAll().stream()
                .skip(auditCountBefore)
                .filter(log -> "USER_PASSWORD_RESET".equals(log.getAction()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(audit.getSummaryJson().contains(temporaryPassword));
    }

    @Test
    void dashboardSeparatesObjectiveFillAndSelfAssessmentMetrics() throws Exception {
        P1TestSupport.SubjectChapter catalog = createSubjectAndChapter();
        P1TestSupport.QuestionRef question = createPublishedChoice(
                catalog.subjectId(), catalog.chapterId(), "Dashboard question", "A", null);
        UUID studentId = registerUser();
        PracticeSession session = createInProgressSession(
                studentId, question.questionId(), question.versionId());
        SessionItem item = sessionItems.findBySessionIdOrderByOrdinalAsc(session.getId()).get(0);
        item.setFinalResult(SessionItem.Result.FAIL);
        sessionItems.save(item);
        session.setStatus(PracticeSession.Status.COMPLETED);
        practiceSessions.save(session);

        mockMvc.perform(get("/api/v1/admin/dashboard").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPracticeSessions").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.completedPracticeSessions").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.objectiveGradedItems").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.objectiveFailedItems").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.questionErrorRate").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.pendingReviewVersions").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.aiRequestCount").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.aiCost").exists());
    }

    @Test
    void ordinaryUsersAndAdministratorsDeletingThemselvesAreRejected() throws Exception {
        UUID userId = registerUser();
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(appUser(userId, usernameFor(userId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .with(appUser(userId, usernameFor(userId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/disable", ADMIN_ID)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private UUID createUser(final String username, final String rawPassword) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setUsernameNormalized(TextNormalizer.normalizeUsername(username));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(username);
        user.setStatus(AppUser.Status.ACTIVE);
        users.save(user);

        UserRole role = new UserRole();
        UserRoleId roleId = new UserRoleId();
        roleId.setUserId(user.getId());
        roleId.setRole(Role.USER);
        role.setId(roleId);
        userRoles.save(role);
        return user.getId();
    }

    private String uniqueUsername(final String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
