package com.billiard.shared.websocket;

import com.billiard.auth.JwtProvider;
import com.billiard.chat.ChatEvents;
import com.billiard.customers.CustomerRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
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
    private final CustomerRepository customerRepository;

    public FloorWebSocketAuthChannelInterceptor(
            JwtProvider jwtProvider,
            UserRepository userRepository,
            CustomerRepository customerRepository
    ) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
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
            authorizeSubscription(accessor);
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

            return new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
        } catch (JwtException exception) {
            throw new AccessDeniedException("Invalid bearer token", exception);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (FloorEventPublisher.FLOOR_TOPIC.equals(destination)
                || ChatEvents.STAFF_TOPIC.equals(destination)) {
            ensureStaff(accessor.getUser());
            return;
        }

        if (destination != null && destination.startsWith("/topic/chat/customer/")) {
            ensureCustomerDestination(accessor.getUser(), destination);
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

    private void ensureCustomerDestination(Principal principal, String destination) {
        if (!(principal instanceof UsernamePasswordAuthenticationToken authentication)
                || authentication.getAuthorities().stream().noneMatch(
                        authority -> "ROLE_CUSTOMER".equals(authority.getAuthority())
                )) {
            throw new AccessDeniedException("Only the owning customer can subscribe to this chat");
        }

        Long customerId = parseCustomerId(destination);
        Long authenticatedCustomerId = customerRepository.findByUser_EmailIgnoreCase(authentication.getName())
                .map(customer -> customer.getId())
                .orElseThrow(() -> new AccessDeniedException("Customer profile not found"));
        if (!authenticatedCustomerId.equals(customerId)) {
            throw new AccessDeniedException("Customers can subscribe only to their own chat");
        }
    }

    private Long parseCustomerId(String destination) {
        String rawId = destination.substring("/topic/chat/customer/".length());
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Invalid customer chat destination", exception);
        }
    }
}
