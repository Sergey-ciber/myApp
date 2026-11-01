package com.demo.taskmanager.dto;

import java.util.List;

/**
 * Содержимое подсказки обычного уровня.
 * Для типа «Первая буква» заполняется firstLetter/firstLetterPatterns, для «Общая подсказка» — text/imageUrl.
 */
public record QuestionHintResponse(String firstLetter, String text, String imageUrl,
                                   List<String> firstLetterPatterns) {
    public QuestionHintResponse(String firstLetter, String text, String imageUrl) {
        this(firstLetter, text, imageUrl, null);
    }
}
