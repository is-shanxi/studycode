package com.certpractice.shared.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Session-safe authenticated principal. It deliberately never contains a
 * password hash or any other credential material.
 */
public record UserPrincipal(
        UUID userId,
        String username,
        Set<Role> roles,
        boolean enabled) implements UserDetails, Serializable {

    public static UserPrincipal of(
            final UUID userId,
            final String username,
            final Collection<Role> roles,
            final boolean enabled) {
        return new UserPrincipal(userId, username, Set.copyOf(roles), enabled);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasRole(final Role role) {
        return roles.contains(role);
    }
}
