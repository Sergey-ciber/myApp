package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.dto.TeamGameStatisticsResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
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
        return ResponseEntity.ok(teamGameService.endGame(sessionId, currentUser(userDetails)));
    }

    /** Контрактный alias: статистика времени по уровням завершённой партии. */
    @GetMapping("/{sessionId}/statistics")
    public ResponseEntity<TeamGameStatisticsResponse> statistics(@PathVariable Long sessionId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamGameService.getStatistics(sessionId, currentUser(userDetails)));
    }

    /** Контрактный alias: открыть следующую подсказку вопроса. */
    @PostMapping("/{sessionId}/questions/{questionId}/hints/reveal")
    public ResponseEntity<TeamGameStateResponse> revealNextHint(@PathVariable Long sessionId,
                                                                @PathVariable Long questionId,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamGameService.revealHint(sessionId, currentUser(userDetails), questionId, null));
    }

    /** Контрактный alias: открыть конкретную подсказку вопроса (идемпотентно). */
    @PostMapping("/{sessionId}/questions/{questionId}/hints/{hintId}/reveal")
    public ResponseEntity<TeamGameStateResponse> revealHint(@PathVariable Long sessionId,
                                                            @PathVariable Long questionId,
                                                            @PathVariable Long hintId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamGameService.revealHint(sessionId, currentUser(userDetails), questionId, hintId));
    }

    private User currentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("Требуется авторизация");
        }
        return userRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }
}
