package com.billiard.shared.config;

import com.billiard.auth.AuthProperties;
import com.billiard.shared.websocket.FloorWebSocketAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthProperties authProperties;
    private final FloorWebSocketAuthChannelInterceptor floorWebSocketAuthChannelInterceptor;

    public WebSocketConfig(
            AuthProperties authProperties,
            FloorWebSocketAuthChannelInterceptor floorWebSocketAuthChannelInterceptor
    ) {
        this.authProperties = authProperties;
        this.floorWebSocketAuthChannelInterceptor = floorWebSocketAuthChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(authProperties.frontendOrigin())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(floorWebSocketAuthChannelInterceptor);
    }
}
