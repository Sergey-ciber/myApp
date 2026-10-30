package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.CheckAnswerRequest;
import com.demo.taskmanager.dto.CheckAnswerResponse;
import com.demo.taskmanager.dto.GameProgressDto;
import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.GameSessionResponse;
import com.demo.taskmanager.dto.GameSummary;
import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.QuestionHintRequest;
import com.demo.taskmanager.dto.QuestionHintResponse;
import com.demo.taskmanager.dto.ResultRequest;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.dto.TimeoutResponse;
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
 * Игра в ассоциации. Играть может любой авторизованный пользователь,
 * создавать, редактировать и удалять игру — только её автор.
 * Прогресс и ответы всегда фильтруются по пользователю из токена.
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

    /**
     * Игра для прохождения: стартовые слова + прогресс текущего пользователя.
     * Ответы колонок 2-5 не отдаются.
     */
    @GetMapping("/{id}")
    public GameSessionResponse get(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long id) {
        return gameService.getGameForPlay(id, currentUser(principal));
    }

    /** Полная игра со всеми словами — только автору, для формы редактирования. */
    @GetMapping("/{id}/edit")
    public GameResponse getForEdit(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long id) {
        return gameService.getGameForEdit(id, currentUser(principal));
    }

    /** Старт/продолжение попытки: таймер считается от серверного времени. */
    @PostMapping("/{id}/start")
    public GameSessionResponse start(@AuthenticationPrincipal UserDetails principal,
                                     @PathVariable Long id) {
        return gameService.startSession(id, currentUser(principal));
    }

    /** Проверка ответа на сервере: клиент не знает правильные слова. */
    @PostMapping("/{id}/check")
    public CheckAnswerResponse check(@AuthenticationPrincipal UserDetails principal,
                                     @PathVariable Long id,
                                     @RequestBody CheckAnswerRequest request) {
        return gameService.checkAnswer(id, currentUser(principal), request);
    }

    /** Начать заново: сбрасывается прогресс только текущего пользователя. */
    @PostMapping("/{id}/restart")
    public GameProgressDto restart(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long id) {
        return gameService.restartProgress(id, currentUser(principal));
    }

    /** Подсказка: первые буквы неразгаданных слов. Доступность проверяется сервером. */
    @PostMapping("/{id}/hint")
    public GameProgressDto hint(@AuthenticationPrincipal UserDetails principal,
                                @PathVariable Long id) {
        return gameService.takeHint(id, currentUser(principal));
    }

    /** Подсказка конкретного вопроса обычного уровня. */
    @PostMapping("/{id}/question-hint")
    public QuestionHintResponse questionHint(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable Long id,
                                             @RequestBody QuestionHintRequest request) {
        return gameService.revealQuestionHint(id, currentUser(principal), request);
    }

    /** Время вышло: раскрываем игроку правильный ответ колонки 5 (без записи результата). */
    @PostMapping("/{id}/timeout")
    public TimeoutResponse timeout(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long id) {
        return gameService.revealTimeoutAnswer(id, currentUser(principal));
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
                                                       @RequestBody(required = false) ResultRequest request) {
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
