package com.certpractice.identity;

import java.io.IOException;

import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.error.ProblemDetailsWriter;
import com.certpractice.shared.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechecks account status on every protected request. Disabling an account
 * therefore invalidates an already-issued session on its next use.
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final ProblemDetailsWriter problemDetailsWriter;

    public ActiveUserFilter(final AppUserRepository users, final ProblemDetailsWriter problemDetailsWriter) {
        this.users = users;
        this.problemDetailsWriter = problemDetailsWriter;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            AppUser user = users.findById(principal.userId()).orElse(null);
            if (user == null || user.getStatus() != AppUser.Status.ACTIVE) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                problemDetailsWriter.write(response, ErrorCode.UNAUTHENTICATED, "Session is no longer valid");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1") || path.startsWith("/api/v1/auth/");
    }
}
