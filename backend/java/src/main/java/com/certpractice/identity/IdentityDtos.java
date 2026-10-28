package com.certpractice.identity;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public identity payloads. No payload exposes password hashes, roles supplied
 * by the client, or other credential material.
 */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String passwordConfirmation) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record SessionResponse(
            UUID userId,
            String username,
            String nickname,
            List<String> roles) {
    }

    public record ProfileResponse(
            UUID userId,
            String username,
            String nickname,
            String status,
            List<String> roles) {
    }

    public record UpdateProfileRequest(
            @Size(max = 64) String nickname) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            @NotBlank String newPasswordConfirmation) {
    }
}
