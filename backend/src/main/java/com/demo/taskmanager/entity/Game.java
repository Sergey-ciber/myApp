package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Игра в ассоциации: название + пирамида из 31 слова.
 * Слова хранятся в отдельной таблице game_words (колонка + позиция внутри колонки).
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Игра, которая открывается в режиме игрока по умолчанию. */
    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    /** Создатель игры: только он может редактировать и удалять её. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("columnNum ASC, position ASC")
    private List<GameWord> words = new ArrayList<>();

    /** Результаты прохождений: удаляются вместе с игрой. */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameResult> results = new ArrayList<>();

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }

    public List<GameWord> getWords() { return words; }

    public List<GameResult> getResults() { return results; }
}
