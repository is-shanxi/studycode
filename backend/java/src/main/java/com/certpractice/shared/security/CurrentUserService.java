package com.certpractice.shared.security;

import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centralized server-side access to the authenticated user and ownership
 * checks. Controllers and services must use these helpers instead of trusting
 * client-supplied identities or frontend route guards.
 */
@Service
public class CurrentUserService {

    public UserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }
        return principal;
    }

    public UUID requireUserId() {
        return requireCurrentUser().userId();
    }

    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal;
    }

    public boolean isAdmin() {
        return isAuthenticated() && requireCurrentUser().hasRole(Role.ADMIN);
    }

    public void requireAdmin() {
        if (!requireCurrentUser().hasRole(Role.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Administrator role is required");
        }
    }

    public void requireOwner(final UUID ownerId) {
        if (!requireUserId().equals(ownerId)) {
            // Hide the existence of other users' private resources.
            throw new ApiException(ErrorCode.NOT_FOUND, "Resource not found");
        }
    }

    public void requireOwnerOrAdmin(final UUID ownerId) {
        UserPrincipal principal = requireCurrentUser();
        if (!principal.userId().equals(ownerId) && !principal.hasRole(Role.ADMIN)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Resource not found");
        }
    }
}
