package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.repository.UserRepository;
import com.demo.taskmanager.service.TeamGameService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class TeamGameWsController {

    private final TeamGameService teamGameService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TeamGameWsController(TeamGameService teamGameService, 
                                UserRepository userRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.teamGameService = teamGameService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/team-game/request-sync")
    public void requestSync(@Payload Map<String, Object> payload, Principal principal) {
        try {
            Long sessionId = payload.get("sessionId") == null ? null : Long.valueOf(String.valueOf(payload.get("sessionId")));
            if (sessionId == null || principal == null) return;
            User user = userRepository.findByUsernameIgnoreCase(principal.getName()).orElse(null);
            if (user == null) return;
            TeamGameStateResponse state = teamGameService.getState(sessionId, user);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("type", "sync_state");
            response.put("sessionId", sessionId);
            response.put("state", state);
            messagingTemplate.convertAndSend("/topic/team_session_" + sessionId, response);
        } catch (Exception e) {
            // игнорируем
        }
    }
}
