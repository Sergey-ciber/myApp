package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Прогресс конкретного пользователя в конкретной игре.
 * Пара (user_id, game_id) уникальна: чужой прогресс получить нельзя,
 * все запросы идут только по паре из токена и id игры.
 */
@Entity
@Table(name = "user_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_progress_user_game", columnNames = {"user_id", "game_id"})
})
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** Разгаданные ячейки: {"2:1":"слон","2:2":"верблюд"} — только ответы этого игрока. */
    @Column(name = "solved_words", nullable = false, columnDefinition = "text")
    private String solvedWords = "{}";

    /** Текущий уровень подсказки: 0 — нет, 1 — первые буквы, 2 — первые две буквы. */
    @Column(name = "hint_level", nullable = false, columnDefinition = "integer default 0")
    private int hintLevel = 0;

    /** Открытые подсказкой префиксы: {"2:1":"с","2:2":"ве"} — только для неразгаданных ячеек. */
    @Column(name = "hint_prefixes", nullable = false, columnDefinition = "text default '{}'")
    private String hintPrefixes = "{}";

    /**
     * Сколько букв открыто подсказками в каждой ячейке: {"2:1":1,"2:2":2}.
     * Нужно, чтобы каждая ячейка получала следующую букву при новом нажатии,
     * даже если другие ячейки стали доступны позже.
     */
    @Column(name = "hints_applied", columnDefinition = "text")
    private String hintsApplied = "{}";

    /** Момент старта текущей попытки — от него считается общий таймер игры. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    /** Индекс текущего уровня (с нуля). Уровни проходятся последовательно. */
    @Column(name = "level_index", nullable = false, columnDefinition = "integer default 0")
    private int levelIndex = 0;

    /**
     * Момент старта текущего уровня — от него считаются таймер уровня
     * и время доступности подсказок. Переживает перезагрузку страницы.
     */
    @Column(name = "level_started_at")
    private Instant levelStartedAt;

    /** Время победы в секундах; null — игра ещё не пройдена. */
    @Column(name = "finished_seconds")
    private Integer finishedSeconds;

    /** Победа текущей попытки уже учтена в рейтинге команд (защита от повторного начисления). */
    @Column(name = "win_counted", nullable = false, columnDefinition = "boolean default false")
    private boolean winCounted = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserProgress() {
    }

    public UserProgress(Game game, User user) {
        this.game = game;
        this.user = user;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public String getSolvedWords() { return solvedWords; }
    public void setSolvedWords(String solvedWords) { this.solvedWords = solvedWords; }

    public int getHintLevel() { return hintLevel; }
    public void setHintLevel(int hintLevel) { this.hintLevel = hintLevel; }

    public String getHintPrefixes() { return hintPrefixes; }
    public void setHintPrefixes(String hintPrefixes) { this.hintPrefixes = hintPrefixes; }

    public String getHintsApplied() { return hintsApplied; }
    public void setHintsApplied(String hintsApplied) { this.hintsApplied = hintsApplied; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public int getLevelIndex() { return levelIndex; }
    public void setLevelIndex(int levelIndex) { this.levelIndex = levelIndex; }

    public Instant getLevelStartedAt() { return levelStartedAt; }
    public void setLevelStartedAt(Instant levelStartedAt) { this.levelStartedAt = levelStartedAt; }

    public Integer getFinishedSeconds() { return finishedSeconds; }
    public void setFinishedSeconds(Integer finishedSeconds) { this.finishedSeconds = finishedSeconds; }

    public boolean isWinCounted() { return winCounted; }
    public void setWinCounted(boolean winCounted) { this.winCounted = winCounted; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
