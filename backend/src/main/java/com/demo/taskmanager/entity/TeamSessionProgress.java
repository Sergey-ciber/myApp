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
}
