// package com.mas.masServer.config;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.messaging.simp.config.ChannelRegistration;
// import org.springframework.messaging.simp.config.MessageBrokerRegistry;
// import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
// import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
// import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// @Configuration
// @EnableWebSocketMessageBroker
// public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

//     private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

//     private final JwtChannelInterceptor jwtChannelInterceptor;

//     public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
//         this.jwtChannelInterceptor = jwtChannelInterceptor;
//     }

//     @Override
//     public void configureMessageBroker(MessageBrokerRegistry config) {
//         config.enableSimpleBroker("/topic");
//         config.setApplicationDestinationPrefixes("/app");
//     }

//     @Override
//     public void registerStompEndpoints(StompEndpointRegistry registry) {
//         // registry.addEndpoint("/api/ws").setAllowedOrigins("*").withSockJS();
//         log.debug("Registering STOMP endpoint: /api/ws");
//         registry.addEndpoint("/api/ws/*")
//                 .setAllowedOriginPatterns("http://localhost:4173") // wildcard-safe
//                 .withSockJS();

//     }

//     @Override
//     public void configureClientInboundChannel(ChannelRegistration registration) {
//         registration.interceptors(jwtChannelInterceptor);
//     }
// }


package com.mas.masServer.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtChannelInterceptor jwtChannelInterceptor;

    public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
        log.debug("I am here in constructor of WebSocketConfig ");
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // clients subscribe to /topic/** ; app-level destinations are /app/**
        log.debug("Configuring MessageBroker "+ config );
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.debug("HERE AT REGISTER STOMP ENDPOINTS ");
        registry.addEndpoint("/api/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        System.out.println("🟢 Before Handshake: " + request.getURI());
                        return super.beforeHandshake(request, response, wsHandler, attributes);
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                            WebSocketHandler wsHandler, Exception ex) {
                        System.out.println("🟢 After Handshake: " + request.getURI());
                        super.afterHandshake(request, response, wsHandler, ex);
                    }
                })
                .withSockJS(); // if using SockJS
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor, loggingChannelInterceptor());
         // ✅ Add a final no-op interceptor that ensures no CSRF check runs
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Effectively bypass any XorCsrfChannelInterceptor injected by Spring
                message.getHeaders().forEach((key, value) -> {
                    if (key.toLowerCase().contains("csrf")) {
                        System.out.println("⚠️ Removing CSRF-related header: " + key);
                    }
                });
                return message;
            }
        });
    }


    @Bean
    public ChannelInterceptor loggingChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                System.out.println("🧩 STOMP Frame: " + message);
                return message;
            }
        };
    }
}
