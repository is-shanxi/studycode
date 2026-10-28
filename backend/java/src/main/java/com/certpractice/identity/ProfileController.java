package com.certpractice.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user profile endpoints. Ownership is derived from the server-side
 * session, never from a client-supplied user id.
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final IdentityService identityService;

    public ProfileController(final IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/profile")
    public IdentityDtos.ProfileResponse profile() {
        return identityService.profile();
    }

    @PatchMapping("/profile")
    public IdentityDtos.ProfileResponse updateProfile(
            @Valid @RequestBody final IdentityDtos.UpdateProfileRequest request) {
        return identityService.updateProfile(request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody final IdentityDtos.ChangePasswordRequest request) {
        identityService.changePassword(request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(final HttpServletRequest request) {
        identityService.anonymizeSelf(request);
    }
}
