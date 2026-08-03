package com.mgmtp.gives.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(AllowedOrigins.parse(allowedOrigins))
                .withSockJS();
        
        // Also support native web socket endpoint without SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(AllowedOrigins.parse(allowedOrigins));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple memory-based message broker to carry the messages back to the client on destinations prefixed with "/topic" and "/queue" (used for user destinations)
        registry.enableSimpleBroker("/topic", "/queue");
        
        // Designates the "/app" prefix for messages that are bound for methods annotated with @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
    }
}
