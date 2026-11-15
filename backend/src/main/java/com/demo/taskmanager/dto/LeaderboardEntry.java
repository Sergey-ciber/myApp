package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.List;

/**
 * Строка рейтинга игры: место, участник (игрок или команда), общее время
 * и детализация прохождения по уровням.
 *
 * В рейтинг попадают полностью завершённые прохождения и попытки,
 * закрытые по времени (общий таймер игры или таймер уровня):
 *  - одиночные — лучшая попытка каждого игрока;
 *  - командные — лучшая зачётная партия каждой команды.
 *
 * Досрочно завершённые хостом партии (HOST_ENDED) в рейтинг не попадают.
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
