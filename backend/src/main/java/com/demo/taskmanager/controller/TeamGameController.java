package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.TeamGameAnswerRequest;
import com.demo.taskmanager.dto.TeamGameHintRequest;
import com.demo.taskmanager.dto.TeamGameLobbyResponse;
import com.demo.taskmanager.dto.TeamGameStartRequest;
import com.demo.taskmanager.dto.TeamGameStateResponse;
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

@RestController
@RequestMapping("/api/team-games")
public class TeamGameController {

    private final TeamGameService teamGameService;
    private final UserRepository userRepository;

    public TeamGameController(TeamGameService teamGameService, UserRepository userRepository) {
        this.teamGameService = teamGameService;
        this.userRepository = userRepository;
    }

    @PostMapping("/lobby")
    public ResponseEntity<TeamGameLobbyResponse> createLobby(@RequestBody TeamGameStartRequest request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.createOrGetLobby(request.teamId(), request.gameId(), user));
    }

    @GetMapping("/lobby/{sessionId}")
    public ResponseEntity<TeamGameLobbyResponse> getLobby(@PathVariable Long sessionId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.getLobby(sessionId, user));
    }

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<TeamGameStateResponse> start(@PathVariable Long sessionId,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.startGame(sessionId, user));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<TeamGameStateResponse> answer(@PathVariable Long sessionId,
                                                        @RequestBody TeamGameAnswerRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.submitAnswer(sessionId, user, request));
    }

    @PostMapping("/{sessionId}/hint")
    public ResponseEntity<TeamGameStateResponse> hint(@PathVariable Long sessionId,
                                                      @RequestBody TeamGameHintRequest request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        return ResponseEntity.ok(teamGameService.requestHint(sessionId, user, request));
    }

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

    @PostMapping("/{sessionId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long sessionId,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUser(userDetails);
        teamGameService.leave(sessionId, user);
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
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }
}
