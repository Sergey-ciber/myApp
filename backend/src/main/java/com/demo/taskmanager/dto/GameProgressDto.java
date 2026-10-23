package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Прогресс текущего пользователя в игре.
 * solved — только те слова, которые угадал этот пользователь: {"2:1":"слон"}.
 * hintLevel — сколько подсказок уже открыто (0..2), hintPrefixes — открытые буквы
 * неразгаданных ячеек: {"2:1":"с","2:2":"ве"}.
 */
public record GameProgressDto(Map<String, String> solved, Instant startedAt,
                              long elapsedSeconds, Integer finishedSeconds,
                              int hintLevel, Map<String, String> hintPrefixes) {
}
