package com.demo.taskmanager.dto;

/** Ответ на открытие игры: структура поля + прогресс именно этого пользователя. */
public record GameSessionResponse(GamePlayResponse game, GameProgressDto progress) {
}
