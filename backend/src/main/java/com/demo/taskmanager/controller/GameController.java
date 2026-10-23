package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.GameSummary;
import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.ResultRequest;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.service.GameService;
import com.demo.taskmanager.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Игра в ассоциации. Список игр и играбельны все игры — для любого авторизованного
 * пользователя; создавать, редактировать и удалять игру может только её автор.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final UserService userService;

    public GameController(GameService gameService, UserService userService) {
        this.gameService = gameService;
        this.userService = userService;
    }

    @GetMapping
    public List<GameSummary> list() {
        return gameService.listGames();
    }

    @GetMapping("/{id}")
    public GameResponse get(@PathVariable Long id) {
        return gameService.getGame(id);
    }

    @PostMapping
    public ResponseEntity<GameResponse> create(@AuthenticationPrincipal UserDetails principal,
                                               @RequestBody GameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gameService.createGame(request, currentUser(principal)));
    }

    @PutMapping("/{id}")
    public GameResponse update(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Long id,
                               @RequestBody GameRequest request) {
        return gameService.updateGame(id, request, currentUser(principal));
    }

    /** Сделать игру активной (открывается игроку по умолчанию). Только автор игры. */
    @PostMapping("/{id}/activate")
    public GameSummary activate(@AuthenticationPrincipal UserDetails principal,
                                @PathVariable Long id) {
        return gameService.activateGame(id, currentUser(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal,
                                       @PathVariable Long id) {
        gameService.deleteGame(id, currentUser(principal));
        return ResponseEntity.noContent().build();
    }

    /** Сохранить время прохождения после победы. */
    @PostMapping("/{id}/results")
    public ResponseEntity<ResultResponse> submitResult(@AuthenticationPrincipal UserDetails principal,
                                                       @PathVariable Long id,
                                                       @RequestBody ResultRequest request) {
        ResultResponse result = gameService.submitResult(id, currentUser(principal),
                request == null ? null : request.timeSeconds());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Рейтинг игры: лучшие результаты по возрастанию времени. */
    @GetMapping("/{id}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable Long id) {
        return gameService.leaderboard(id);
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
