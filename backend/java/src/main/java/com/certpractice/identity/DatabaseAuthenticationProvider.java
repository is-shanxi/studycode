package com.certpractice.identity;

import java.util.Set;
import java.util.stream.Collectors;

import com.certpractice.shared.security.Role;
import com.certpractice.shared.security.UserPrincipal;
import com.certpractice.shared.text.TextNormalizer;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates against app_user without ever persisting the password hash in
 * the session principal.
 */
@Component
public class DatabaseAuthenticationProvider implements AuthenticationProvider {

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;

    public DatabaseAuthenticationProvider(
            final AppUserRepository users,
            final UserRoleRepository userRoles,
            final PasswordEncoder passwordEncoder) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
        String presentedUsername = authentication.getName() == null ? "" : authentication.getName();
        Object credentials = authentication.getCredentials();
        String presentedPassword = credentials == null ? "" : credentials.toString();
        String normalizedUsername = TextNormalizer.normalizeUsername(presentedUsername);

        AppUser user = users.findByUsernameNormalized(normalizedUsername)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (user.getStatus() != AppUser.Status.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        if (!passwordEncoder.matches(presentedPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        Set<Role> roles = userRoles.findByIdUserId(user.getId())
                .stream()
                .map(UserRole::getId)
                .map(UserRoleId::getRole)
                .collect(Collectors.toUnmodifiableSet());

        UserPrincipal principal = UserPrincipal.of(user.getId(), user.getUsername(), roles, true);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities());
    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
