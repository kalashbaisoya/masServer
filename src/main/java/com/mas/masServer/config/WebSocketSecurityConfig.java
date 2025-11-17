package com.mas.masServer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
// import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager.Builder;
// import org.springframework.security.messaging.context.MessageAuthorizationContext;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig{
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        
                log.debug("Configuring messageAuthorizationManager at bean AuthorizationManager "+messages);
        messages
            // Allow connection setup/disconnect if authenticated
            .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.DISCONNECT, SimpMessageType.OTHER)
            .authenticated()

            // Secure subscription to specific topic
            .simpSubscribeDestMatchers("/topic/group/*/membership-status")
            .access((auth, context) -> {
                Message<?> message = context.getMessage();
                String destination = (String) message.getHeaders().get("simpDestination");

                if (destination == null) {
                    return new AuthorizationDecision(false);
                }

                // Expected destination: /topic/group/{groupId}/membership-status
                String[] parts = destination.split("/");
                String groupId = parts.length > 3 ? parts[3] : null;

                if (groupId == null) {
                    return new AuthorizationDecision(false);
                }

                // Check if user has appropriate group role
                boolean authorized = auth.get().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("GROUP_ROLE_GROUP_MANAGER"));

                return new AuthorizationDecision(authorized);
            })

            // Authenticated users can send heartbeats
            .simpDestMatchers("/app/heartbeat").authenticated()

            // Default rule
            .anyMessage().authenticated();

        return messages.build();
    }
}
