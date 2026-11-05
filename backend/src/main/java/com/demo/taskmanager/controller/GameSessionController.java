package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.UserRepository;
import com.demo.taskmanager.service.TeamGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контрактный alias для завершения игровой сессии:
 * POST /api/game-sessions/{sessionId}/end.
 *
 * Полностью делегирует проверку прав и завершение в TeamGameService;
 * основной UI использует /api/team-games/{sessionId}/end.
 */
@RestController
@RequestMapping("/api/game-sessions")
public class GameSessionController {

    private final TeamGameService teamGameService;
    private final UserRepository userRepository;

    public GameSessionController(TeamGameService teamGameService, UserRepository userRepository) {
        this.teamGameService = teamGameService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<TeamGameStateResponse> end(@PathVariable Long sessionId,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("Требуется авторизация");
        }
        User user = userRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return ResponseEntity.ok(teamGameService.endGame(sessionId, user));
    }
}
