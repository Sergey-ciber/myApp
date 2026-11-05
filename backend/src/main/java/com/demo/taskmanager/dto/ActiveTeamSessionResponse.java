package com.demo.taskmanager.dto;

import java.time.Instant;

/**
 * Карточка активной командной сессии для списка игр.
 *
 * Показывается игроку, если в одной из его команд идёт активная партия:
 * название игры, кто её запустил (хост) и id сессии для присоединения.
 */
public record ActiveTeamSessionResponse(
        Long sessionId,
        Long teamId,
        String teamName,
        String teamAvatarUrl,
        Long gameId,
        String gameTitle,
        Long hostId,
        String hostUsername,
        String status,
        int currentLevelIndex,
        int levelsCount,
        Instant startedAt
) {
}
