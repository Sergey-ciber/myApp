package com.demo.taskmanager.dto;

/** Ответ на запрос истечения времени: раскрываем только слово колонки 5. */
public record TimeoutResponse(String answer) {
}
