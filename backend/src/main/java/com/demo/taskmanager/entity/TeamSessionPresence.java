package com.demo.taskmanager.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Отметка присутствия участника командной сессии.
 *
 * Запись живёт, пока игрок участвует в партии: обычный heartbeat только
 * обновляет last_seen_at, а joined_at остаётся неизменным — по нему
 * детерминированно выбирается новый host, если прежний явно вышел из игры.
 */
@Entity
@Table(name = "team_session_presence")
public class TeamSessionPresence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TeamGameSession session;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 100)
    private String username;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    /**
     * Момент присоединения участника к сессии.
     *
     * Не меняется при heartbeat. Nullable — чтобы добавление колонки к
     * уже существующей таблице проходило без ошибки; для старых записей
     * значение восстанавливается из last_seen_at при первом обновлении.
     */
    @Column(name = "joined_at")
    private Instant joinedAt;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
