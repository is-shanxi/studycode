package com.certpractice.identity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.security.CurrentUserService;
import com.certpractice.shared.security.Role;
import com.certpractice.shared.security.UserPrincipal;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, profile, password and soft-delete workflows. Sessions
 * are stored server-side; the browser only receives an HttpOnly cookie.
 */
@Service
public class IdentityService {

    private static final int BCRYPT_MAX_PASSWORD_LENGTH = 72;

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbcTemplate;

    public IdentityService(
            final AppUserRepository users,
            final UserRoleRepository userRoles,
            final PasswordEncoder passwordEncoder,
            final AuthenticationManager authenticationManager,
            final SecurityContextRepository securityContextRepository,
            final CurrentUserService currentUserService,
            final JdbcTemplate jdbcTemplate) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.currentUserService = currentUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IdentityDtos.SessionResponse register(final IdentityDtos.RegisterRequest request) {
        String normalized = TextNormalizer.normalizeUsername(request.username());
        Guard.require(normalized.length() >= 3, ErrorCode.VALIDATION_FAILED, "username is too short");
        Guard.require(request.password().equals(request.passwordConfirmation()),
                ErrorCode.VALIDATION_FAILED, "password confirmation does not match");
        Guard.require(request.password().length() <= BCRYPT_MAX_PASSWORD_LENGTH,
                ErrorCode.VALIDATION_FAILED, "password is too long");
        if (users.existsByUsernameNormalized(normalized)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Username is already registered");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setUsernameNormalized(normalized);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.username().trim());
        user.setStatus(AppUser.Status.ACTIVE);
        users.save(user);

        UserRole userRole = new UserRole();
        UserRoleId roleId = new UserRoleId();
        roleId.setUserId(user.getId());
        roleId.setRole(Role.USER);
        userRole.setId(roleId);
        userRoles.save(userRole);

        return sessionResponse(user, List.of(Role.USER));
    }

    @Transactional(readOnly = true)
    public IdentityDtos.SessionResponse login(
            final IdentityDtos.LoginRequest request,
            final HttpServletRequest httpRequest,
            final HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        AppUser user = requireUser(principal.userId());
        return sessionResponse(user, roleEnums(principal.userId()));
    }

    public void logout(final HttpServletRequest request, final HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie("XSRF-TOKEN", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @Transactional(readOnly = true)
    public IdentityDtos.SessionResponse currentSession() {
        UUID userId = currentUserService.requireUserId();
        AppUser user = requireUser(userId);
        return sessionResponse(user, roleEnums(userId));
    }

    @Transactional(readOnly = true)
    public IdentityDtos.ProfileResponse profile() {
        UUID userId = currentUserService.requireUserId();
        AppUser user = requireUser(userId);
        return new IdentityDtos.ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus().name(),
                roleNames(userId));
    }

    @Transactional
    public IdentityDtos.ProfileResponse updateProfile(final IdentityDtos.UpdateProfileRequest request) {
        UUID userId = currentUserService.requireUserId();
        AppUser user = requireUser(userId);
        String nickname = request.nickname() == null ? null : request.nickname().trim();
        user.setNickname(nickname == null || nickname.isBlank() ? null : nickname);
        users.save(user);
        return new IdentityDtos.ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus().name(),
                roleNames(userId));
    }

    @Transactional
    public void changePassword(final IdentityDtos.ChangePasswordRequest request) {
        UUID userId = currentUserService.requireUserId();
        AppUser user = requireUser(userId);
        Guard.require(request.newPassword().equals(request.newPasswordConfirmation()),
                ErrorCode.VALIDATION_FAILED, "password confirmation does not match");
        Guard.require(request.newPassword().length() <= BCRYPT_MAX_PASSWORD_LENGTH,
                ErrorCode.VALIDATION_FAILED, "password is too long");
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        invalidateSessions(user.getUsername());
    }

    @Transactional
    public void anonymizeSelf(final HttpServletRequest request) {
        UUID userId = currentUserService.requireUserId();
        AppUser user = requireUser(userId);
        if (currentUserService.isAdmin()) {
            throw new ApiException(ErrorCode.ADMIN_CANNOT_DELETE_SELF, "Administrator cannot delete self");
        }

        String anonymousName = "deleted-" + user.getId();
        user.setUsername(anonymousName);
        user.setUsernameNormalized(anonymousName);
        user.setNickname(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(AppUser.Status.DELETED_ANONYMIZED);
        user.setDeletedAt(Instant.now());
        users.save(user);
        invalidateSessions(user.getUsername());

        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public void requireOwnedUser(final UUID userId) {
        currentUserService.requireOwner(userId);
    }

    public List<Role> roleEnums(final UUID userId) {
        return userRoles.findByIdUserId(userId).stream()
                .map(UserRole::getId)
                .map(UserRoleId::getRole)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public List<String> roleNames(final UUID userId) {
        return roleEnums(userId).stream().map(Enum::name).toList();
    }

    private AppUser requireUser(final UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Session user no longer exists"));
    }

    private void invalidateSessions(final String principalName) {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", principalName);
    }

    private IdentityDtos.SessionResponse sessionResponse(final AppUser user, final List<Role> roles) {
        return new IdentityDtos.SessionResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                roles.stream().map(Enum::name).toList());
    }
}
