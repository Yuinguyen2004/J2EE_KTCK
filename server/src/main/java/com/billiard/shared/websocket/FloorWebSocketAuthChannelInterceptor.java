package com.billiard.shared.websocket;

import com.billiard.auth.JwtProvider;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class FloorWebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public FloorWebSocketAuthChannelInterceptor(
            JwtProvider jwtProvider,
            UserRepository userRepository
    ) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
            mutableAccessor.setLeaveMutable(true);
            mutableAccessor.setUser(authenticate(mutableAccessor));
            return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            ensureStaff(accessor.getUser());
        }

        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Floor WebSocket requires a bearer token");
        }

        try {
            Claims claims = jwtProvider.validateAccessToken(authorization.substring(7));
            User user = userRepository.findByEmailIgnoreCase(claims.getSubject())
                    .filter(User::isActive)
                    .orElseThrow(() -> new AccessDeniedException("User is not active"));
            if (user.getRole() == UserRole.CUSTOMER) {
                throw new AccessDeniedException("Customers cannot subscribe to floor updates");
            }

            return new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
        } catch (JwtException exception) {
            throw new AccessDeniedException("Invalid bearer token", exception);
        }
    }

    private void ensureStaff(Principal principal) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken authentication)
                || authentication.getAuthorities().stream().anyMatch(
                        authority -> "ROLE_CUSTOMER".equals(authority.getAuthority())
                )) {
            throw new AccessDeniedException("Only staff clients can subscribe to floor updates");
        }
    }
}
