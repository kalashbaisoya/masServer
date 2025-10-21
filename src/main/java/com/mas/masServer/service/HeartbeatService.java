package com.mas.masServer.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeartbeatService {

    private final Map<String, LocalDateTime> heartbeatTimestamps = new ConcurrentHashMap<>();

    public void updateHeartbeat(String username) {
        heartbeatTimestamps.put(username, LocalDateTime.now());
    }

    public LocalDateTime getLastHeartbeat(String username) {
        return heartbeatTimestamps.get(username);
    }

    public void removeHeartbeat(String username) {
        heartbeatTimestamps.remove(username);
    }

    public Set<String> getUsernames(){
        return heartbeatTimestamps.keySet();
    }
}
