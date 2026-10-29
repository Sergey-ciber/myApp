package com.demo.taskmanager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "standard_answers")
public class StandardAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private StandardQuestion question;

    @Column(name = "answer_text", columnDefinition = "text", nullable = false)
    private String answerText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StandardQuestion getQuestion() { return question; }
    public void setQuestion(StandardQuestion question) { this.question = question; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
}
