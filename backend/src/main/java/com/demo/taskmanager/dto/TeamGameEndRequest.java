package com.demo.taskmanager.dto;

/**
 * Запрос на завершение командной игры.
 * Выполнить может только хост сессии (тот, кто её запустил).
 */
public record TeamGameEndRequest(Long sessionId, String reason) {
}
