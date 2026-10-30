package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Игра: может содержать несколько уровней.
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "game_date")
    private LocalDate gameDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Игра, которая открывается в режиме игрока по умолчанию. */
    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    /** Лимит времени на прохождение в минутах (может быть дробным, например 2.5). */
    @Column(name = "time_limit", columnDefinition = "double precision default 10")
    private Double timeLimitMinutes = 10.0;

    /** Сложность игры: EASY, MEDIUM, HARD. */
    @Column(length = 20)
    private String difficulty = "MEDIUM";

    /** Создатель игры: только он может редактировать и удалять её. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("columnNum ASC, position ASC")
    private List<GameWord> words = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<GameLevel> levels = new ArrayList<>();

    /** Результаты прохождений: удаляются вместе с игрой. */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameResult> results = new ArrayList<>();

    /** Прогресс игроков: удаляется вместе с игрой. */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProgress> progressEntries = new ArrayList<>();

    /** Добавить слово в колонку columnNum (1..5) на позицию position (1..N). */
    public void addWord(int columnNum, int position, String word) {
        GameWord gameWord = new GameWord();
        gameWord.setGame(this);
        gameWord.setColumnNum(columnNum);
        gameWord.setPosition(position);
        gameWord.setWord(word);
        words.add(gameWord);
    }

    public void clearWords() {
        words.clear();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getGameDate() { return gameDate; }
    public void setGameDate(LocalDate gameDate) { this.gameDate = gameDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Double getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(Double timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }

    public List<GameWord> getWords() { return words; }
    public List<GameLevel> getLevels() { return levels; }
    public void setLevels(List<GameLevel> levels) { this.levels = levels; }

    public List<GameResult> getResults() { return results; }

    public List<UserProgress> getProgressEntries() { return progressEntries; }
}
