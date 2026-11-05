package com.demo.taskmanager.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_game_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_game_progress", columnNames = {"user_id", "game_id"})
})
public class UserGameProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "current_level_index", nullable = false)
    private int currentLevelIndex = 0;

    @Column(name = "current_question_index", nullable = false)
    private int currentQuestionIndex = 0;

    @Column(name = "game_state_json", columnDefinition = "text")
    private String gameStateJson = "{}";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public int getCurrentLevelIndex() { return currentLevelIndex; }
    public void setCurrentLevelIndex(int currentLevelIndex) { this.currentLevelIndex = currentLevelIndex; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public String getGameStateJson() { return gameStateJson; }
    public void setGameStateJson(String gameStateJson) { this.gameStateJson = gameStateJson; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
