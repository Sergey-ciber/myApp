package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Прогресс текущего пользователя в игре.
 * solved — только те слова, которые угадал этот пользователь: {"2:1":"слон"}.
 * hintLevel — сколько нажатий «Получить подсказку» уже использовано.
 * hintPrefixes — открытые подсказкой буквы неразгаданных ячеек: {"2:1":"с","2:2":"ве"}.
 * hintsApplied — сколько букв открыто подсказками в каждой ячейке: {"2:1":1,"2:2":2}.
 * levelIndex — текущий уровень игры (с нуля).
 * levelElapsedSeconds — время с начала текущего уровня; именно от него считаются
 * таймер уровня и доступность подсказок. Не сбрасывается при перезагрузке страницы.
 */
public record GameProgressDto(Map<String, String> solved, Instant startedAt,
                              long elapsedSeconds, Integer finishedSeconds,
                              int hintLevel, Map<String, String> hintPrefixes,
                              Map<String, Integer> hintsApplied,
                              int levelIndex, long levelElapsedSeconds) {
}
