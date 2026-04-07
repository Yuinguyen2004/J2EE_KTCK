package com.billiard.shared.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.billiard.auth.JwtProvider;
import com.billiard.chat.ChatEvents;
import com.billiard.customers.Customer;
import com.billiard.customers.CustomerRepository;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class FloorWebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    private FloorWebSocketAuthChannelInterceptor floorWebSocketAuthChannelInterceptor;

    @BeforeEach
    void setUp() {
        floorWebSocketAuthChannelInterceptor = new FloorWebSocketAuthChannelInterceptor(
                jwtProvider,
                userRepository,
                customerRepository
        );
    }

    @Test
    void connectAuthenticatesStaffBearerToken() {
        User staff = buildUser("staff@example.com", UserRole.STAFF);
        when(jwtProvider.validateAccessToken("access-token")).thenReturn(claimsFor(staff.getEmail()));
        when(userRepository.findByEmailIgnoreCase(staff.getEmail())).thenReturn(Optional.of(staff));

        Message<?> message = connectMessage("Bearer access-token");
        Message<?> intercepted = floorWebSocketAuthChannelInterceptor.preSend(message, null);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(intercepted);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo(staff.getEmail());
    }

    @Test
    void connectRejectsMissingBearerToken() {
        assertThatThrownBy(() -> floorWebSocketAuthChannelInterceptor.preSend(connectMessage(null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeRejectsCustomerPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(FloorEventPublisher.FLOOR_TOPIC);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "customer@example.com",
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        ));

        assertThatThrownBy(() -> floorWebSocketAuthChannelInterceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                null
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeAllowsCustomerOnOwnedChatTopic() {
        User customerUser = buildUser("customer@example.com", UserRole.CUSTOMER);
        customerUser.setId(8L);
        Customer customer = new Customer();
        customer.setId(18L);
        customer.setUser(customerUser);

        when(customerRepository.findByUser_EmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(customer));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(ChatEvents.customerTopic(18L));
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "customer@example.com",
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
        ));

        Message<?> intercepted = floorWebSocketAuthChannelInterceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()),
                null
        );

        assertThat(intercepted).isNotNull();
    }

    private static Claims claimsFor(String email) {
        return Jwts.claims().subject(email).build();
    }

    private static Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static User buildUser(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
