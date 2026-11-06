package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.List;

/**
 * Статистика времени командной игры по уровням.
 *
 * Все значения уже посчитаны на сервере в секундах:
 *  - pureSeconds    — чистое время уровня (старт → завершение или таймаут);
 *  - penaltySeconds — суммарный штраф за подсказки уровня;
 *  - totalSeconds   — pureSeconds + penaltySeconds.
 *
 * totalSeconds верхнего уровня — сумма totalSeconds по всем пройденным уровням.
 */
public record TeamGameStatisticsResponse(
        Long sessionId,
        String status,
        String endReason,
        Instant startedAt,
        Instant endedAt,
        long totalSeconds,
        List<LevelStat> levels
) {

    public record LevelStat(
            Long levelId,
            int levelIndex,
            String title,
            Instant startedAt,
            Instant endedAt,
            long pureSeconds,
            long penaltySeconds,
            long totalSeconds,
            boolean timedOut
    ) {
    }
}
