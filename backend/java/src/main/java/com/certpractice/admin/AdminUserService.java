package com.certpractice.admin;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.identity.AppUser;
import com.certpractice.identity.AppUserRepository;
import com.certpractice.identity.UserRole;
import com.certpractice.identity.UserRoleRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator user management: query, disable, enable and one-time password
 * reset. Every mutation is audited; the reset password is never persisted or
 * logged in clear text.
 */
@Service
public class AdminUserService {

    private static final char[] TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$".toCharArray();
    private static final int TEMP_PASSWORD_LENGTH = 16;

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AdminUserService(
            final AppUserRepository users,
            final UserRoleRepository userRoles,
            final PasswordEncoder passwordEncoder,
            final JdbcTemplate jdbcTemplate,
            final AdminAuditService auditService) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminUserView> search(
            final String keyword,
            final String status,
            final PageQuery pageQuery) {
        AppUser.Status parsedStatus = parseStatus(status);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<AppUser> page = users.search(normalizedKeyword, parsedStatus, pageQuery.toPageable());
        List<AdminDtos.AdminUserView> items = page.getContent().stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional
    public AdminDtos.AdminUserView disable(final UUID adminUserId, final UUID userId) {
        if (adminUserId.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Administrators cannot disable their own account");
        }
        AppUser user = requireUser(userId);
        if (user.getStatus() == AppUser.Status.DELETED_ANONYMIZED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Deleted accounts cannot be disabled");
        }
        if (user.getStatus() != AppUser.Status.DISABLED) {
            user.setStatus(AppUser.Status.DISABLED);
            users.save(user);
            invalidateSessions(user.getUsername());
            auditService.record(
                    adminUserId,
                    "USER_DISABLE",
                    "APP_USER",
                    userId,
                    AdminAuditLog.Outcome.SUCCESS,
                    Map.of("username", user.getUsername()));
        }
        return toView(user);
    }

    @Transactional
    public AdminDtos.AdminUserView enable(final UUID adminUserId, final UUID userId) {
        AppUser user = requireUser(userId);
        if (user.getStatus() == AppUser.Status.DELETED_ANONYMIZED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Deleted accounts cannot be re-enabled");
        }
        if (user.getStatus() != AppUser.Status.ACTIVE) {
            user.setStatus(AppUser.Status.ACTIVE);
            users.save(user);
            auditService.record(
                    adminUserId,
                    "USER_ENABLE",
                    "APP_USER",
                    userId,
                    AdminAuditLog.Outcome.SUCCESS,
                    Map.of("username", user.getUsername()));
        }
        return toView(user);
    }

    @Transactional
    public AdminDtos.ResetPasswordResponse resetPassword(final UUID adminUserId, final UUID userId) {
        AppUser user = requireUser(userId);
        if (user.getStatus() == AppUser.Status.DELETED_ANONYMIZED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Deleted accounts cannot be reset");
        }
        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        users.save(user);
        invalidateSessions(user.getUsername());
        auditService.record(
                adminUserId,
                "USER_PASSWORD_RESET",
                "APP_USER",
                userId,
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("username", user.getUsername(), "delivery", "ONE_TIME_DISPLAY"));
        return new AdminDtos.ResetPasswordResponse(
                user.getId(), user.getUsername(), temporaryPassword);
    }

    private AppUser requireUser(final UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private AdminDtos.AdminUserView toView(final AppUser user) {
        List<String> roles = userRoles.findByIdUserId(user.getId()).stream()
                .map(UserRole::getId)
                .map(role -> role.getRole().name())
                .sorted(Comparator.naturalOrder())
                .toList();
        return new AdminDtos.AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus().name(),
                roles,
                user.getCreatedAt());
    }

    private AppUser.Status parseStatus(final String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AppUser.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported user status");
        }
    }

    private void invalidateSessions(final String principalName) {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", principalName);
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int index = 0; index < TEMP_PASSWORD_LENGTH; index++) {
            password.append(TEMP_PASSWORD_CHARS[random.nextInt(TEMP_PASSWORD_CHARS.length)]);
        }
        return password.toString();
    }
}
