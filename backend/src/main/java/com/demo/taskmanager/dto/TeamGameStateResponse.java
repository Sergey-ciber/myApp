package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Полное состояние командной сессии — «слепок» игры для подключающегося участника.
 *
 * Содержит текущий уровень, разгаданные слова, штраф, использованные подсказки,
 * список участников с отметкой «онлайн», id хоста (того, кто может завершить игру)
 * и серверные данные жизненного цикла партии: время старта, длительность,
 * плановое и фактическое время завершения, причину завершения.
 */
public record TeamGameStateResponse(
        Long sessionId,
        String status,
        int currentLevelIndex,
        GamePlayResponse game,
        Map<String, String> solved,
        double penaltyTime,
        Map<String, Object> hints,
        Instant startTime,
        Instant startedAt,
        Instant levelEndTime,
        List<Map<String, Object>> events,
        List<String> onlineUserIds,
        Map<String, String> memberNames,
        Long hostId,
        String hostUsername,
        boolean currentUserHost,
        Double durationMinutes,
        Instant endsAt,
        Instant endedAt,
        String endReason
) {
    public boolean active() {
        return "active".equalsIgnoreCase(status);
    }

    public boolean finished() {
        return "finished".equalsIgnoreCase(status);
    }
}
