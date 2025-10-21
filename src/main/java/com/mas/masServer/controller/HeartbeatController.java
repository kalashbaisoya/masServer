package com.mas.masServer.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.mas.masServer.service.HeartbeatService;

import org.springframework.security.core.context.SecurityContextHolder;

@Controller
public class HeartbeatController {

    @Autowired
    private HeartbeatService heartbeatService;

    @MessageMapping("/heartbeat")
    public void receiveHeartbeat() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        heartbeatService.updateHeartbeat(username);
    }
}
