package com.demo.taskmanager.dto;

/** Проверка ответа на сервере: клиент присылает слово, но не знает правильное. */
public record CheckAnswerRequest(Integer column, Integer position, String answer) {
}
