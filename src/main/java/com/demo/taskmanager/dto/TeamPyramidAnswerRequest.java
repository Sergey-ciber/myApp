package com.demo.taskmanager.dto;

/**
 * Запрос проверки ответа для уровня типа «Игра в ассоциации» (пирамида).
 * Указывает колонку (2–5) и позицию в ней.
 */
public record TeamPyramidAnswerRequest(Long sessionId, Integer column, Integer position, String answer) {}
