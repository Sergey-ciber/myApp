package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Время прохождения уровня в командной игре.
 *
 * Запись создаётся при старте уровня и закрывается, когда уровень завершён:
 * все вопросы разгаданы, истекло время уровня или игру завершил хост.
 *
 * Все значения считаются на сервере и сохраняются в БД, поэтому статистика
 * одинакова для всех участников и переживает перезагрузку страницы.
 */
@Entity
@Table(name = "team_level_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_level_stat_session_level",
                columnNames = {"session_id", "level_id"}),
        indexes = @Index(name = "idx_team_level_stats_session", columnList = "session_id"))
public class TeamLevelStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TeamGameSession session;

    /** id уровня игры (game_levels.id) — не FK, чтобы редактирование игры не ломало историю. */
    @Column(name = "level_id", nullable = false)
    private Long levelId;

    /** Порядковый номер уровня в партии (с 0) — по нему строится таблица. */
    @Column(name = "level_index", nullable = false)
    private int levelIndex;

    /** Название уровня на момент прохождения. */
    @Column(length = 255)
    private String title;

    /** Момент начала уровня. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Момент завершения уровня; null — уровень ещё идёт. */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** Чистое время уровня в секундах: от старта до завершения (или таймаута). */
    @Column(name = "pure_seconds", nullable = false)
    private long pureSeconds;

    /** Суммарный штраф за подсказки уровня в секундах. */
    @Column(name = "penalty_seconds", nullable = false)
    private long penaltySeconds;

    /** Уровень завершился по истечении отведённого времени. */
    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

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

    public Long getLevelId() {
        return levelId;
    }

    public void setLevelId(Long levelId) {
        this.levelId = levelId;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(int levelIndex) {
        this.levelIndex = levelIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public long getPureSeconds() {
        return pureSeconds;
    }

    public void setPureSeconds(long pureSeconds) {
        this.pureSeconds = pureSeconds;
    }

    public long getPenaltySeconds() {
        return penaltySeconds;
    }

    public void setPenaltySeconds(long penaltySeconds) {
        this.penaltySeconds = penaltySeconds;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }
}
