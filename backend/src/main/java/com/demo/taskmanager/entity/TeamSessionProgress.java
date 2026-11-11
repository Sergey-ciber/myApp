package com.demo.taskmanager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "team_session_progress")
public class TeamSessionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TeamGameSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private GameLevel level;

    @Column(name = "solved_data", columnDefinition = "text")
    private String solvedData = "{}";

    @Column(name = "penalty_time")
    private Double penaltyTime = 0.0;

    /** Сколько раз команда использовала подсказку-пирамиду на этом уровне. */
    @Column(name = "hint_level")
    private Integer hintLevel = 0;

    /** Открытые буквы подсказки по ячейкам пирамиды: {"column:position": "префикс"}. */
    @Column(name = "hint_prefixes", columnDefinition = "text")
    private String hintPrefixes = "{}";

    /** Сколько букв открыто в каждой ячейке: {"column:position": число}. */
    @Column(name = "hints_applied", columnDefinition = "text")
    private String hintsApplied = "{}";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TeamGameSession getSession() {
        return session;
    }

    public void setSession(TeamGameSession session) {
        this.session = session;
    }

    public GameLevel getLevel() {
        return level;
    }

    public void setLevel(GameLevel level) {
        this.level = level;
    }

    public String getSolvedData() {
        return solvedData;
    }

    public void setSolvedData(String solvedData) {
        this.solvedData = solvedData;
    }

    public double getPenaltyTime() {
        return penaltyTime == null ? 0.0 : penaltyTime;
    }

    public void setPenaltyTime(double penaltyTime) {
        this.penaltyTime = penaltyTime;
    }

    public int getHintLevel() {
        return hintLevel == null ? 0 : hintLevel;
    }

    public void setHintLevel(int hintLevel) {
        this.hintLevel = hintLevel;
    }

    public String getHintPrefixes() {
        return hintPrefixes;
    }

    public void setHintPrefixes(String hintPrefixes) {
        this.hintPrefixes = hintPrefixes;
    }

    public String getHintsApplied() {
        return hintsApplied;
    }

    public void setHintsApplied(String hintsApplied) {
        this.hintsApplied = hintsApplied;
    }
}
