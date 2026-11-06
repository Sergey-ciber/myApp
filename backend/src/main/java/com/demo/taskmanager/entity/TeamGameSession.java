package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Серверная сессия командной игры (co-op).
 * 
 * Игра живёт на сервере независимо от подключений игроков. Таймеры и логика
 * работают на бэкенде. WebSocket используется только для синхронизации UI.
 */
@Entity
@Table(name = "team_sessions", indexes = {
        @Index(name = "idx_team_sessions_team_status", columnList = "team_id, status"),
        @Index(name = "idx_team_sessions_status", columnList = "status")
})
public class TeamGameSession {

    public static final String STATUS_LOBBY = "lobby";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FINISHED = "finished";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_ACTIVE_OLD = "ACTIVE";
    public static final String STATUS_FINISHED_OLD = "FINISHED";

    public static final String END_REASON_HOST_ENDED = "host_ended";
    public static final String END_REASON_TIME_EXPIRED = "time_expired";
    public static final String END_REASON_COMPLETED = "completed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** Хост сессии - может завершить игру */
    @Column(name = "host_id", nullable = false)
    private Long hostId;

    /** Логин хоста на момент старта */
    @Column(name = "host_username", length = 50)
    private String hostUsername;

    /** Статус сессии: active, finished */
    @Column(name = "session_status", length = 20)
    private String sessionStatus;

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    /** Индекс текущего уровня */
    @Column(name = "current_level_index")
    private Integer currentLevelIndex;

    @Column(name = "current_level")
    private Integer currentLevel;

    /** Полный стейт игры в JSON */
    @Column(name = "state_data", columnDefinition = "text")
    private String stateData;

    /** Снимок состояния партии в формате JSON (для обратной совместимости) */
    @Column(name = "game_state", columnDefinition = "text")
    private String gameStateJson = "{}";

    /** Время окончания текущего уровня */
    @Column(name = "level_end_time")
    private Instant levelEndTime;

    /** Момент старта текущего уровня — от него отсчитываются задержки подсказок. */
    @Column(name = "level_started_at")
    private Instant levelStartedAt;

    @Column(name = "start_time")
    private Instant startTime;

    /** Момент старта партии (дублирует start_time для явного контракта). */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Зафиксированная на старте длительность партии в минутах. */
    @Column(name = "duration_minutes")
    private Double durationMinutes;

    /** Момент автоматического завершения: started_at + duration_minutes. */
    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Момент фактического завершения партии. */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** Причина завершения: host_ended, time_expired, completed. */
    @Column(name = "end_reason", length = 30)
    private String endReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    public String getHostUsername() {
        return hostUsername;
    }

    public void setHostUsername(String hostUsername) {
        this.hostUsername = hostUsername;
    }

    public String getSessionStatus() {
        if (sessionStatus != null) return sessionStatus;
        return status;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
        this.status = sessionStatus;
    }

    public String getStatus() {
        if (status != null && (status.equals(STATUS_ACTIVE_OLD) || status.equals(STATUS_FINISHED_OLD))) {
            return status.toLowerCase();
        }
        if (sessionStatus != null) return sessionStatus;
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.sessionStatus = status;
    }

    public boolean isActive() {
        String s = getStatus();
        return STATUS_ACTIVE.equalsIgnoreCase(s) || STATUS_ACTIVE_OLD.equals(s);
    }

    public boolean isFinished() {
        String s = getStatus();
        return STATUS_FINISHED.equalsIgnoreCase(s) || STATUS_FINISHED_OLD.equals(s);
    }

    public int getCurrentLevelIndex() {
        if (currentLevelIndex != null) return currentLevelIndex;
        if (currentLevel != null) return currentLevel;
        return 0;
    }

    public void setCurrentLevelIndex(int currentLevelIndex) {
        this.currentLevelIndex = currentLevelIndex;
        this.currentLevel = currentLevelIndex;
    }

    public Integer getCurrentLevel() {
        return currentLevelIndex != null ? currentLevelIndex : currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
        this.currentLevelIndex = currentLevel;
    }

    public String getStateData() {
        if (stateData != null) return stateData;
        return gameStateJson;
    }

    public void setStateData(String stateData) {
        this.stateData = stateData;
        this.gameStateJson = stateData;
    }

    public String getGameStateJson() {
        if (gameStateJson != null && !"{}".equals(gameStateJson)) return gameStateJson;
        return stateData != null ? stateData : "{}";
    }

    public void setGameStateJson(String gameStateJson) {
        this.gameStateJson = gameStateJson;
        this.stateData = gameStateJson;
    }

    public Instant getLevelEndTime() {
        return levelEndTime;
    }

    public void setLevelEndTime(Instant levelEndTime) {
        this.levelEndTime = levelEndTime;
    }

    public Instant getLevelStartedAt() {
        return levelStartedAt;
    }

    public void setLevelStartedAt(Instant levelStartedAt) {
        this.levelStartedAt = levelStartedAt;
    }

    public Instant getStartTime() {
        return startedAt != null ? startedAt : startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
        this.startedAt = startTime;
    }

    public Instant getStartedAt() {
        return getStartTime();
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
        if (startedAt != null) {
            this.startTime = startedAt;
        }
    }

    public Double getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Double durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getFinishedAt() {
        return endedAt != null ? endedAt : finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
        this.endedAt = finishedAt;
    }

    public Instant getEndedAt() {
        return getFinishedAt();
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
        if (endedAt != null) {
            this.finishedAt = endedAt;
        }
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }
}
