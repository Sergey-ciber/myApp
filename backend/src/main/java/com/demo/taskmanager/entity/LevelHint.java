package com.demo.taskmanager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "level_hints")
public class LevelHint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private GameLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HintType type = HintType.FIRST_LETTER;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

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
    public HintType getType() { return type; }
    public void setType(HintType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Double getTriggerTime() { return triggerTime; }
    public void setTriggerTime(Double triggerTime) { this.triggerTime = triggerTime; }
    public Double getPenaltyTime() { return penaltyTime; }
    public void setPenaltyTime(Double penaltyTime) { this.penaltyTime = penaltyTime; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
