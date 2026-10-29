package com.demo.taskmanager.dto;

/** Проверка ответа на сервере: клиент присылает текст ответа, правильные варианты не отдаются игроку. */
public record CheckAnswerRequest(Integer column, Integer position, String answer, Long questionId) {
}
