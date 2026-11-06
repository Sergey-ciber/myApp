package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Факт открытия подсказки вопроса командной игры.
 *
 * Запись создаётся один раз при открытии и больше не удаляется: подсказка
 * остаётся видимой всем участникам команды до завершения уровня/игры.
 */
@Entity
@Table(name = "team_hint_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_hint_state",
                columnNames = {"session_id", "hint_id"}),
        indexes = {
                @Index(name = "idx_team_hint_states_session_level", columnList = "session_id, level_id"),
                @Index(name = "idx_team_hint_states_question", columnList = "session_id, question_id")
        })
public class TeamHintState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TeamGameSession session;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "level_id", nullable = false)
    private Long levelId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "hint_id", nullable = false)
    private Long hintId;

    @Column(name = "is_revealed", nullable = false)
    private boolean revealed = true;

    @Column(name = "revealed_at")
    private Instant revealedAt;

    @Column(name = "revealed_by_user_id")
    private Long revealedByUserId;

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
    public Long getHintId() { return hintId; }
    public void setHintId(Long hintId) { this.hintId = hintId; }
    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }
    public Instant getRevealedAt() { return revealedAt; }
    public void setRevealedAt(Instant revealedAt) { this.revealedAt = revealedAt; }
    public Long getRevealedByUserId() { return revealedByUserId; }
    public void setRevealedByUserId(Long revealedByUserId) { this.revealedByUserId = revealedByUserId; }
}
