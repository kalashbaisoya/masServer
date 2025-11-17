// package com.mas.masServer.controller;


// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.messaging.handler.annotation.MessageMapping;
// import org.springframework.stereotype.Controller;

// import com.mas.masServer.service.HeartbeatService;

// import org.springframework.security.core.context.SecurityContextHolder;

// @Controller
// public class HeartbeatController {

//     @Autowired
//     private HeartbeatService heartbeatService;

//     private static final Logger log = LoggerFactory.getLogger(HeartbeatController.class);

//     @MessageMapping("/heartbeat")
//     public void receiveHeartbeat() {
//         log.debug("Received heartbeat from user: {}", SecurityContextHolder.getContext().getAuthentication().getName());
//         String username = SecurityContextHolder.getContext().getAuthentication().getName();
//         heartbeatService.updateHeartbeat(username);
//     }
// }

package com.mas.masServer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.mas.masServer.service.HeartbeatService;

import java.security.Principal;

@Controller
public class HeartbeatController {

    @Autowired
    private HeartbeatService heartbeatService;

    private static final Logger log = LoggerFactory.getLogger(HeartbeatController.class);

    @MessageMapping("/heartbeat")
    public void receiveHeartbeat(Principal principal) {
        if (principal == null) {
            log.warn("Received heartbeat from unauthenticated principal");
            return;
        }
        String username = principal.getName();
        log.debug("Received heartbeat from user: {}", username);
        heartbeatService.updateHeartbeat(username);
    }
}
