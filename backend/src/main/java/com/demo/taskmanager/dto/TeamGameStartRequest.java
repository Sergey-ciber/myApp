package com.demo.taskmanager.dto;

/**
 * Запрос на запуск командной игры.
 * Сессия создаётся в момент вызова — игрок становится хостом.
 */
public record TeamGameStartRequest(Long teamId, Long gameId) {
}
