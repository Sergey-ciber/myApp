package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Результат прохождения игры: кто, за сколько секунд и когда разгадал колонку 5.
 * У одного пользователя может быть сколько угодно попыток — в рейтинг идёт лучшая.
 */
@Entity
@Table(name = "results", indexes = {
        @Index(name = "idx_results_game_time", columnList = "game_id, time_seconds")
})
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "time_seconds", nullable = false)
    private int timeSeconds;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt = Instant.now();

    public GameResult() {
    }

    public GameResult(Game game, User user, int timeSeconds) {
        this.game = game;
        this.user = user;
        this.timeSeconds = timeSeconds;
    }

    public Long getId() { return id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(int timeSeconds) { this.timeSeconds = timeSeconds; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
