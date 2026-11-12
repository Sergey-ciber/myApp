package com.demo.taskmanager.dto;

import java.util.List;

/**
 * Результат прохождения, который присылает клиент после победы.
 *
 * timeSeconds — общее время (остаётся для совместимости),
 * levelsStats — детализация по уровням. По ней сервер проверяет,
 * что игра действительно пройдена до конца: если уровней меньше,
 * чем в игре, или какой-то уровень закончился по таймауту,
 * результат не сохраняется.
 */
public record ResultRequest(
        Integer timeSeconds,
        List<LevelStatsDto> levelsStats
) {
}
