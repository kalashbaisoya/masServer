package com.mas.masServer.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
// import java.util.List;
// import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);
    private final JwtUtil jwtUtil;

    public JwtChannelInterceptor(JwtUtil jwtUtil) {
        log.debug("I am in constructor of this class JwtChannelInterceptor ");
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            log.warn("Accessor is null");
            System.out.println("Accessor"+accessor);
            return message;
        }

        log.debug("Processing STOMP command: {}", accessor.getCommand());

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("✅ STOMP CONNECT from user: {}", accessor.getUser());
            // STOMP native headers can include the Authorization header on CONNECT frames.
            // String authHeader = accessor.getFirstNativeHeader("Authorization");
            // if (authHeader == null) {
            //     // Some clients may send lower-case header or 'authorization' - check variants:
            //     authHeader = accessor.getFirstNativeHeader("authorization");
            // }

            // if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            //     // Don't throw a raw runtime exception (which makes handshake cryptic).
            //     log.warn("Missing Authorization header in STOMP CONNECT");
            //     return message; // Connection will proceed without principal; security rules will reject subscriptions/sends if necessary.
            // }

            // String token = authHeader.substring(7);
            try {
            //     if (!jwtUtil.validateToken(token)) {
            //         log.warn("Invalid/expired JWT in STOMP CONNECT");
            //         return message;
            //     }

            //     String username = jwtUtil.extractUsername(token);
            //     List<SimpleGrantedAuthority> authorities = jwtUtil.extractAuthorities(token)
            //             .stream()
            //             .map(SimpleGrantedAuthority::new)
            //             .collect(Collectors.toList());

            //     UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            //             username, null, authorities);
                accessor.setUser(accessor.getUser());
                log.debug("Set STOMP user principal for");
            } catch (Exception e) {
                log.error("Error processing JWT in STOMP CONNECT: {}", e.getMessage());
                // don't throw; leave unauthenticated principal (security will handle)
            }
        }

        return message;
    }
}
