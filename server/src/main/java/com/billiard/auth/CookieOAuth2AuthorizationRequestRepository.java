package com.billiard.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * Stores the OAuth2 authorization request in an encrypted cookie so that
 * the stateless session policy does not prevent the OAuth2 redirect flow.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 180;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthRequestDto(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            String authorizationRequestUri,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes
    ) {}

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeCookie(response);
            return;
        }

        String serialized = serialize(authorizationRequest);
        Cookie cookie = new Cookie(COOKIE_NAME, serialized);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authRequest = getCookie(request);
        if (authRequest != null) {
            removeCookie(response);
        }
        return authRequest;
    }

    private OAuth2AuthorizationRequest getCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return deserialize(cookie.getValue());
            }
        }
        return null;
    }

    private void removeCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            AuthRequestDto dto = new AuthRequestDto(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    request.getState(),
                    request.getAuthorizationRequestUri(),
                    request.getAdditionalParameters(),
                    request.getAttributes()
            );
            byte[] json = MAPPER.writeValueAsBytes(dto);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", ex);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(value);
            AuthRequestDto dto = MAPPER.readValue(json, AuthRequestDto.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(dto.authorizationUri())
                    .clientId(dto.clientId())
                    .redirectUri(dto.redirectUri())
                    .scopes(dto.scopes())
                    .state(dto.state())
                    .authorizationRequestUri(dto.authorizationRequestUri())
                    .additionalParameters(dto.additionalParameters() != null
                            ? dto.additionalParameters() : Map.of())
                    .attributes(a -> {
                        if (dto.attributes() != null) {
                            a.putAll(dto.attributes());
                        }
                    })
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }
}
