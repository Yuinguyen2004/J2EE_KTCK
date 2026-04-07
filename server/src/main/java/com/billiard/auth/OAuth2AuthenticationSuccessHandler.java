package com.billiard.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final AuthService authService;
    private final AuthProperties authProperties;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            AuthProperties authProperties
    ) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new ServletException("OAuth2 user details are unavailable");
        }

        Boolean emailVerified = oauth2User.getAttribute("email_verified");
        if (Boolean.FALSE.equals(emailVerified)) {
            redirectStrategy.sendRedirect(
                    request,
                    response,
                    buildErrorRedirect(
                            "oauth_email_unverified",
                            "Google email must be verified before sign-in"
                    )
            );
            return;
        }

        try {
            String exchangeCode = authService.issueGoogleExchangeCode(
                    requiredAttribute(
                            oauth2User,
                            "email",
                            "Google account email is required for sign-in"
                    ),
                    optionalAttribute(oauth2User, "name")
            );

            redirectStrategy.sendRedirect(
                    request,
                    response,
                    UriComponentsBuilder.fromUriString(authProperties.oauth2CallbackUrl())
                            .queryParam("code", exchangeCode)
                            .build()
                            .encode()
                            .toUriString()
            );
        } catch (ResponseStatusException ex) {
            redirectStrategy.sendRedirect(
                    request,
                    response,
                    buildErrorRedirect("oauth_exchange_failed", ex.getReason())
            );
        }
    }

    private String requiredAttribute(
            OAuth2User oauth2User,
            String attributeName,
            String errorMessage
    ) {
        String value = optionalAttribute(oauth2User, attributeName);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value;
    }

    private String optionalAttribute(OAuth2User oauth2User, String attributeName) {
        Object value = oauth2User.getAttribute(attributeName);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private String buildErrorRedirect(String code, String description) {
        return UriComponentsBuilder.fromUriString(authProperties.oauth2CallbackUrl())
                .queryParam("error", code)
                .queryParam(
                        "error_description",
                        description == null ? "Google sign-in failed" : description
                )
                .build()
                .encode()
                .toUriString();
    }
}
