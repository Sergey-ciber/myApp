package com.demo.taskmanager.dto;

/** Результат проверки ответа и обновлённый прогресс этого пользователя. */
public record CheckAnswerResponse(boolean correct, GameProgressDto progress, String answer) {
}
