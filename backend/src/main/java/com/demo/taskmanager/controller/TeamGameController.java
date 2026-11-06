package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.ActiveTeamSessionResponse;
import com.demo.taskmanager.dto.TeamGameAnswerRequest;
import com.demo.taskmanager.dto.TeamGameHintRequest;
import com.demo.taskmanager.dto.TeamGameLobbyResponse;
import com.demo.taskmanager.dto.TeamGameStartRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Командные игры (co-op).
 *
 * Жизненный цикл сессии:
 *  POST /api/team-games/start   — создать сессию и стать хостом;
 *  GET  /api/team-games/active  — активные партии моих команд (карточки в списке игр);
 *  POST /api/team-games/{id}/join — присоединиться к идущей партии;
 *  POST /api/team-games/{id}/end  — завершить партию (только хост, иначе 403).
 */
@RestController
@RequestMapping("/api/team-games")
public class TeamGameController {

    private final TeamGameService teamGameService;
    private final UserRepository userRepository;

    public TeamGameController(TeamGameService teamGameService, UserRepository userRepository) {
        this.teamGameService = teamGameService;
        this.userRepository = userRepository;
    }

    /** Активные партии команд, в которых состоит текущий пользователь. */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveTeamSessionResponse>> activeSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamGameService.activeSessionsFor(currentUser(userDetails)));
    }

    /** Подготовка к игре: возвращает лобби команды и, если партия уже идёт, её сессию. */
    @PostMapping("/lobby")
    public ResponseEntity<TeamGameLobbyResponse> openLobby(@RequestBody TeamGameStartRequest request,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.openLobby(request.teamId(), request.gameId(), user));
    }

    /** Запуск командной игры: создаёт сессию, автор становится хостом. */
    @PostMapping("/start")
    public ResponseEntity<TeamGameStateResponse> start(@RequestBody TeamGameStartRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.startGame(request.teamId(), request.gameId(), user));
    }

    /** Полный слепок состояния партии — по нему игрок попадает на текущий момент игры. */
    @GetMapping("/{sessionId}/state")
    public ResponseEntity<TeamGameStateResponse> state(@PathVariable Long sessionId,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.getState(sessionId, user));
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<TeamGameStateResponse> join(@PathVariable Long sessionId,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.join(sessionId, user));
    }

    /** Ответ: правильный ответ рассылается всем участникам комнаты. */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<TeamGameStateResponse> answer(@PathVariable Long sessionId,
                                                        @RequestBody TeamGameAnswerRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.submitAnswer(sessionId, user, request));
    }

    /** Подсказка: штраф начисляется, событие рассылается всем участникам. */
    @PostMapping("/{sessionId}/hint")
    public ResponseEntity<TeamGameStateResponse> hint(@PathVariable Long sessionId,
                                                      @RequestBody TeamGameHintRequest request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.requestHint(sessionId, user, request));
    }

    /** Открыть следующую по порядку подсказку конкретного вопроса. */
    @PostMapping("/{sessionId}/questions/{questionId}/hints/reveal")
    public ResponseEntity<TeamGameStateResponse> revealNextHint(@PathVariable Long sessionId,
                                                                @PathVariable Long questionId,
                                                                @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.revealHint(sessionId, user, questionId, null));
    }

    /** Открыть конкретную подсказку вопроса; повторный вызов идемпотентен. */
    @PostMapping("/{sessionId}/questions/{questionId}/hints/{hintId}/reveal")
    public ResponseEntity<TeamGameStateResponse> revealHint(@PathVariable Long sessionId,
                                                            @PathVariable Long questionId,
                                                            @PathVariable Long hintId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.revealHint(sessionId, user, questionId, hintId));
    }

    /** Завершение партии — только хост. Для остальных возвращается 403. */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<TeamGameStateResponse> end(@PathVariable Long sessionId,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.endGame(sessionId, user));
    }

    /** Статистика времени по уровням: доступна всем участникам команды. */
    @GetMapping("/{sessionId}/statistics")
    public ResponseEntity<TeamGameStatisticsResponse> statistics(@PathVariable Long sessionId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.getStatistics(sessionId, user));
    }

    /** Выход из партии: игра продолжается, хост передаёт права следующему игроку. */
    @PostMapping("/{sessionId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long sessionId,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        teamGameService.leave(sessionId, user);
        return ResponseEntity.ok().build();
    }

    /** Отключение (закрыта вкладка): участник пропадает, партия продолжается. */
    @PostMapping("/{sessionId}/disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable Long sessionId,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        teamGameService.disconnect(sessionId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable Long sessionId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        teamGameService.heartbeat(sessionId, user);
        return ResponseEntity.ok().build();
    }

    private User currentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("Требуется авторизация");
        }
        return userRepository.findByUsernameIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }
}
