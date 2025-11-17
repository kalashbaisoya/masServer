package com.mas.masServer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class WebSocketCsrfRemover implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketCsrfRemover.class);

    @Autowired
    private ApplicationContext ctx;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            ExecutorSubscribableChannel inbound = ctx.getBean("clientInboundChannel", ExecutorSubscribableChannel.class);

            List<ChannelInterceptor> toRemove = new ArrayList<>();
            for (ChannelInterceptor interceptor : inbound.getInterceptors()) {
                String className = interceptor.getClass().getName();
                if (className.contains("XorCsrfChannelInterceptor") || className.contains("CsrfChannelInterceptor")) {
                    toRemove.add(interceptor);
                    log.info("🧹 Marked CSRF interceptor for removal: {}", className);
                }
            }

            for (ChannelInterceptor i : toRemove) {
                boolean removed = inbound.removeInterceptor(i);
                log.info("✅ Removed interceptor {} -> {}", i.getClass().getName(), removed);
            }

            log.info("📋 Remaining inbound interceptors: {}", inbound.getInterceptors().stream()
                    .map(i -> i.getClass().getSimpleName())
                    .toList());
        } catch (Exception ex) {
            log.error("❌ Failed to remove XorCsrfChannelInterceptor from clientInboundChannel", ex);
        }
    }
}
