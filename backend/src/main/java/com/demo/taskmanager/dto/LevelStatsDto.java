package com.demo.taskmanager.dto;

import java.util.List;

/**
 * Детализация прохождения одного уровня в рейтинге игры.
 *
 * Все значения уже в секундах — форматирование делает клиент.
 * Для одиночной игры источник — данные, присланные вместе с результатом,
 * для командной — таблица team_level_stats (считается на сервере).
 */
public record LevelStatsDto(
        Long levelId,
        String levelName,
        int netSeconds,
        int penaltySeconds,
        int totalSeconds,
        boolean timedOut
) {

    /** Сумма времени уровня: чистое время + штраф за подсказки. */
    public int total() {
        return netSeconds + penaltySeconds;
    }

    public static LevelStatsDto of(Long levelId, String levelName,
                                   int netSeconds, int penaltySeconds, boolean timedOut) {
        int net = Math.max(0, netSeconds);
        int penalty = Math.max(0, penaltySeconds);
        return new LevelStatsDto(levelId, levelName, net, penalty, net + penalty, timedOut);
    }

    /** Список детализации с пересчётом итогов — защита от рассинхрона на клиенте. */
    public static List<LevelStatsDto> normalize(List<LevelStatsDto> levels) {
        if (levels == null) return List.of();
        return levels.stream()
                .filter(l -> l != null)
                .map(l -> of(l.levelId(), l.levelName(), l.netSeconds(), l.penaltySeconds(), l.timedOut()))
                .toList();
    }
}
