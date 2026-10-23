package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Прогресс текущего пользователя в игре.
 * solved — только те слова, которые угадал этот пользователь: {"2:1":"слон"}.
 */
public record GameProgressDto(Map<String, String> solved, Instant startedAt,
                              long elapsedSeconds, Integer finishedSeconds) {
}
