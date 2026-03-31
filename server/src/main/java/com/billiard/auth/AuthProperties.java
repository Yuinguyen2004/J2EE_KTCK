package com.billiard.auth;

import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String frontendBaseUrl = "http://localhost:5173";
    private String mailFrom = "no-reply@billiard-shop.local";
    private String refreshCookieName = "refresh_token";
    private String refreshCookiePath = "/api/v1/auth";
    private boolean refreshCookieSecure = true;
    private Duration passwordResetTokenTtl = Duration.ofMinutes(30);
    private Duration oauthExchangeCodeTtl = Duration.ofMinutes(1);
    private String oauth2CallbackPath = "/oauth2/callback";

    public String frontendOrigin() {
        URI uri = URI.create(frontendBaseUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            return frontendBaseUrl;
        }

        StringBuilder origin = new StringBuilder(uri.getScheme())
                .append("://")
                .append(uri.getHost());

        if (uri.getPort() != -1) {
            origin.append(":").append(uri.getPort());
        }

        return origin.toString();
    }

    public String oauth2CallbackUrl() {
        return URI.create(frontendBaseUrl).resolve(normalizedCallbackPath()).toString();
    }

    private String normalizedCallbackPath() {
        if (oauth2CallbackPath == null || oauth2CallbackPath.isBlank()) {
            return "/oauth2/callback";
        }

        return oauth2CallbackPath.startsWith("/")
                ? oauth2CallbackPath
                : "/" + oauth2CallbackPath;
    }
}
