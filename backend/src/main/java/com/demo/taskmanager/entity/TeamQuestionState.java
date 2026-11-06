package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Состояние вопроса командной игры для конкретной сессии и команды.
 *
 * Хранится на сервере независимо от браузера: перезагрузка страницы,
 * выход и повторный вход не теряют прогресс по вопросу.
 */
@Entity
@Table(name = "team_question_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_question_state",
                columnNames = {"session_id", "question_id"}),
        indexes = {
                @Index(name = "idx_team_question_states_session_level", columnList = "session_id, level_id"),
                @Index(name = "idx_team_question_states_team", columnList = "team_id")
        })
public class TeamQuestionState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TeamGameSession session;

    /** Команда-владелец прогресса (дублируется для явного контракта). */
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "level_id", nullable = false)
    private Long levelId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "is_solved", nullable = false)
    private boolean solved = false;

    @Column(name = "solved_at")
    private Instant solvedAt;

    @Column(name = "solved_by_user_id")
    private Long solvedByUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TeamGameSession getSession() { return session; }
    public void setSession(TeamGameSession session) { this.session = session; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getLevelId() { return levelId; }
    public void setLevelId(Long levelId) { this.levelId = levelId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public boolean isSolved() { return solved; }
    public void setSolved(boolean solved) { this.solved = solved; }
    public Instant getSolvedAt() { return solvedAt; }
    public void setSolvedAt(Instant solvedAt) { this.solvedAt = solvedAt; }
    public Long getSolvedByUserId() { return solvedByUserId; }
    public void setSolvedByUserId(Long solvedByUserId) { this.solvedByUserId = solvedByUserId; }
}
