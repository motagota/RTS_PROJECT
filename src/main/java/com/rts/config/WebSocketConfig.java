package com.rts.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration supporting both:
 * 1. STOMP over SockJS for lobby/game features (JSON messages)
 * 2. Native WebSocket for map streaming (binary messages)
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    private final BinaryWebSocketHandler binaryWebSocketHandler;

    // ========== Native WebSocket for Binary Map Streaming ==========

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register binary WebSocket handler for map generation
        registry.addHandler(binaryWebSocketHandler, "/ws/map-generation/{sessionId}")
                .setAllowedOrigins("*");
    }

    // ========== STOMP over SockJS for Lobby/Game Features ==========

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
