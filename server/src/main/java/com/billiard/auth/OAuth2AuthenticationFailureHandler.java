package com.billiard.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final AuthProperties authProperties;

    public OAuth2AuthenticationFailureHandler(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        redirectStrategy.sendRedirect(
                request,
                response,
                UriComponentsBuilder.fromUriString(authProperties.oauth2CallbackUrl())
                        .queryParam("error", "oauth_failure")
                        .queryParam("error_description", "Google sign-in failed")
                        .build()
                        .encode()
                        .toUriString()
        );
    }
}
