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

    /** Момент старта текущей попытки — от него считается таймер. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    /** Время победы в секундах; null — игра ещё не пройдена. */
    @Column(name = "finished_seconds")
    private Integer finishedSeconds;

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

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Integer getFinishedSeconds() { return finishedSeconds; }
    public void setFinishedSeconds(Integer finishedSeconds) { this.finishedSeconds = finishedSeconds; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
