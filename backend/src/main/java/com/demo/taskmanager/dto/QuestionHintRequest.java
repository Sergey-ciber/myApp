package com.demo.taskmanager.dto;

/** Запрос на раскрытие подсказки конкретного вопроса обычного уровня. */
public record QuestionHintRequest(Long questionId, Integer hintIndex) {
}
