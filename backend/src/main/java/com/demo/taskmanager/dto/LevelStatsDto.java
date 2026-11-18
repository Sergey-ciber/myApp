package com.demo.taskmanager.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Детализация прохождения одного уровня в рейтинге игры.
 *
 * Все значения уже в секундах — форматирование делает клиент.
 * Для одиночной игры источник — данные, присланные вместе с результатом,
 * для командной — таблица team_level_stats (считается на сервере).
 *
 * includeTimeInStats — учитывать ли уровень в общей статистике/рейтинге.
 * Флаг берётся из текущих настроек уровня при чтении: старые записи без
 * поля считаются включёнными (true), чтобы поведение не менялось.
 */
public record LevelStatsDto(
        Long levelId,
        String levelName,
        int netSeconds,
        int penaltySeconds,
        int totalSeconds,
        boolean timedOut,
        /** Учитывать ли время уровня в итогах. null/отсутствие = true (совместимость). */
        Boolean includeTimeInStats
) {

    public LevelStatsDto {
        includeTimeInStats = includeTimeInStats == null || includeTimeInStats;
    }

    /** Сумма времени уровня: чистое время + штраф за подсказки. */
    public int total() {
        return netSeconds + penaltySeconds;
    }

    /** Учитывается ли уровень в общей статистике; по умолчанию true. */
    public boolean countsInTotals() {
        return includeTimeInStats == null || includeTimeInStats;
    }

    public static LevelStatsDto of(Long levelId, String levelName,
                                   int netSeconds, int penaltySeconds, boolean timedOut) {
        return of(levelId, levelName, netSeconds, penaltySeconds, timedOut, true);
    }

    public static LevelStatsDto of(Long levelId, String levelName,
                                   int netSeconds, int penaltySeconds, boolean timedOut,
                                   Boolean includeTimeInStats) {
        int net = Math.max(0, netSeconds);
        int penalty = Math.max(0, penaltySeconds);
        boolean include = includeTimeInStats == null || includeTimeInStats;
        return new LevelStatsDto(levelId, levelName, net, penalty, net + penalty, timedOut, include);
    }

    public LevelStatsDto withIncludeTimeInStats(boolean include) {
        return new LevelStatsDto(levelId, levelName, netSeconds, penaltySeconds, totalSeconds, timedOut, include);
    }

    /** Список детализации с пересчётом итогов — защита от рассинхрона на клиенте. */
    public static List<LevelStatsDto> normalize(List<LevelStatsDto> levels) {
        if (levels == null || levels.isEmpty()) return List.of();
        List<LevelStatsDto> result = new ArrayList<>(levels.size());
        for (LevelStatsDto level : levels) {
            if (level == null) continue;
            result.add(of(level.levelId(), level.levelName(),
                    level.netSeconds(), level.penaltySeconds(), level.timedOut(),
                    level.includeTimeInStats()));
        }
        return result;
    }

    /** Сумма totalSeconds только по уровням, учитываемым в итогах. */
    public static int effectiveTotal(List<LevelStatsDto> levels) {
        if (levels == null || levels.isEmpty()) return 0;
        int sum = 0;
        for (LevelStatsDto level : levels) {
            if (level != null && level.countsInTotals()) {
                sum += level.total();
            }
        }
        return sum;
    }
}
