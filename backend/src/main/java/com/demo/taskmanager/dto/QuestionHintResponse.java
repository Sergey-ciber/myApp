package com.demo.taskmanager.dto;

/**
 * Содержимое подсказки обычного уровня.
 * Для типа «Первая буква» заполняется firstLetter, для «Общая подсказка» — text/imageUrl.
 */
public record QuestionHintResponse(String firstLetter, String text, String imageUrl) {
}
