package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.GameSummary;
import com.demo.taskmanager.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Игра в ассоциации. Игры общие для всех пользователей:
 * администратор создаёт, игрок открывает и разгадывает.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
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
    public ResponseEntity<GameResponse> create(@RequestBody GameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(request));
    }

    @PutMapping("/{id}")
    public GameResponse update(@PathVariable Long id, @RequestBody GameRequest request) {
        return gameService.updateGame(id, request);
    }

    /** Сделать игру активной (открывается игроку по умолчанию). */
    @PostMapping("/{id}/activate")
    public GameSummary activate(@PathVariable Long id) {
        return gameService.activateGame(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gameService.deleteGame(id);
        return ResponseEntity.noContent().build();
    }
}
