package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "standard_questions")
public class StandardQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private GameLevel level;

    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    @Column(name = "media_type", length = 20)
    private String mediaType; // image, audio, video

    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    /** Список URL медиафайлов вопроса (максимум 10). */
    @ElementCollection
    @CollectionTable(name = "standard_question_media", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "url", columnDefinition = "text")
    @OrderColumn(name = "order_index")
    private List<String> mediaUrls = new ArrayList<>();

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    /** Порог прохождения: сколько вариантов ответа нужно дать, чтобы вопрос считался разгаданным. */
    @Column(name = "pass_threshold")
    private Integer passThreshold;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StandardAnswer> answers = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public GameLevel getLevel() { return level; }
    public void setLevel(GameLevel level) { this.level = level; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public Integer getPassThreshold() { return passThreshold; }
    public void setPassThreshold(Integer passThreshold) { this.passThreshold = passThreshold; }
    public List<StandardAnswer> getAnswers() { return answers; }
}
