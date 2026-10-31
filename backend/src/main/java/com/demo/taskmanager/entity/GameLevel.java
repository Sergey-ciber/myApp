package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_levels")
public class GameLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(name = "time_limit", nullable = false)
    private double timeLimitMinutes = 10.0;

    /** Список URL медиафайлов уровня (максимум 10). */
    @ElementCollection
    @CollectionTable(name = "level_media", joinColumns = @JoinColumn(name = "level_id"))
    @Column(name = "url", columnDefinition = "text")
    @OrderColumn(name = "order_index")
    private List<String> mediaUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LevelType type = LevelType.NORMAL;

    @Column(name = "hints_count", nullable = false)
    private int hintsCount = 0;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<LevelHint> hints = new ArrayList<>();

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("columnNum ASC, position ASC")
    private List<LevelWord> words = new ArrayList<>();

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<LevelQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<StandardQuestion> standardQuestions = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public double getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(double timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
    public LevelType getType() { return type; }
    public void setType(LevelType type) { this.type = type; }
    public int getHintsCount() { return hintsCount; }
    public void setHintsCount(int hintsCount) { this.hintsCount = hintsCount; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public List<LevelHint> getHints() { return hints; }
    public List<LevelWord> getWords() { return words; }
    public List<LevelQuestion> getQuestions() { return questions; }
    public List<StandardQuestion> getStandardQuestions() { return standardQuestions; }
}
