package com.demo.taskmanager.entity;

import java.util.Locale;

/**
 * Причина завершения партии.
 *
 * Для одиночной игры хранится в {@code results.finish_reason} и
 * {@code user_progress.finish_reason}; для командной — в
 * {@code team_sessions.end_reason} (поле переиспользуется, отдельной колонки нет).
 *
 * {@link #USER_ABORTED} используется только как причина завершения попытки:
 * результат в {@code results} для неё не создаётся.
 */
public enum FinishReason {

    COMPLETED,
    TIME_EXPIRED,
    LEVEL_TIME_EXPIRED,
    USER_ABORTED,
    HOST_ENDED;

    /** Причина завершения по времени (общий таймер игры или таймер уровня). */
    public boolean isTimeout() {
        return this == TIME_EXPIRED || this == LEVEL_TIME_EXPIRED;
    }

    /** Засчитывается ли партия в таблицу результатов и рейтинг игры. */
    public boolean countsForLeaderboard() {
        return this == COMPLETED || isTimeout();
    }

    /** Значение для legacy-колонки end_reason (в БД исторически хранится в нижнем регистре). */
    public String storageValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Разбор значения из БД или запроса: принимает и UPPER_CASE, и lower_case. */
    public static FinishReason fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (FinishReason reason : values()) {
            if (reason.name().equals(normalized)) {
                return reason;
            }
        }
        return null;
    }
}
