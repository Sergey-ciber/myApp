package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.List;

/**
 * Строка рейтинга игры: место, участник (игрок или команда), общее время
 * и детализация прохождения по уровням.
 *
 * В рейтинг попадают только полностью завершённые прохождения:
 *  - одиночные — лучшая попытка каждого игрока;
 *  - командные — лучшая завершённая партия каждой команды.
 *
 * Прерванные игры (хост завершил партию досрочно, истекло время)
 * в рейтинг не попадают никогда.
 */
public record LeaderboardEntry(
        int rank,
        Long userId,
        Long teamId,
        String playerName,
        boolean team,
        int timeSeconds,
        Instant completedAt,
        List<LevelStatsDto> levelsStats
) {
}
