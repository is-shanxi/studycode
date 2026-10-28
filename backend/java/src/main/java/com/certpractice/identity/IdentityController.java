package com.certpractice.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Registration always creates a USER account even if
 * the client sends extra role fields, which are ignored by the request DTO.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(final IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDtos.SessionResponse register(
            @Valid @RequestBody final IdentityDtos.RegisterRequest request) {
        return identityService.register(request);
    }

    @PostMapping("/sessions")
    public IdentityDtos.SessionResponse login(
            @Valid @RequestBody final IdentityDtos.LoginRequest request,
            final HttpServletRequest httpRequest,
            final HttpServletResponse httpResponse) {
        return identityService.login(request, httpRequest, httpResponse);
    }

    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(final HttpServletRequest request, final HttpServletResponse response) {
        identityService.logout(request, response);
    }

    @GetMapping("/me")
    public IdentityDtos.SessionResponse me() {
        return identityService.currentSession();
    }
}
