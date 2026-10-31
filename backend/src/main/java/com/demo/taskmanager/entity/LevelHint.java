package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "level_hints")
public class LevelHint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private GameLevel level;

    /** Подсказка может быть привязана к конкретному вопросу обычного уровня. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private StandardQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HintType type = HintType.FIRST_LETTER;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    /** Список URL медиафайлов подсказки (максимум 10). */
    @ElementCollection
    @CollectionTable(name = "level_hint_media", joinColumns = @JoinColumn(name = "hint_id"))
    @Column(name = "url", columnDefinition = "text")
    @OrderColumn(name = "order_index")
    private List<String> mediaUrls = new ArrayList<>();

    @Column(name = "trigger_time")
    private Double triggerTime;

    /** Штрафное время (в минутах), которое прибавляется к времени уровня при использовании подсказки. */
    @Column(name = "penalty_time")
    private Double penaltyTime;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GameLevel getLevel() { return level; }
    public void setLevel(GameLevel level) { this.level = level; }
    public StandardQuestion getQuestion() { return question; }
    public void setQuestion(StandardQuestion question) { this.question = question; }
    public HintType getType() { return type; }
    public void setType(HintType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
    public Double getTriggerTime() { return triggerTime; }
    public void setTriggerTime(Double triggerTime) { this.triggerTime = triggerTime; }
    public Double getPenaltyTime() { return penaltyTime; }
    public void setPenaltyTime(Double penaltyTime) { this.penaltyTime = penaltyTime; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
