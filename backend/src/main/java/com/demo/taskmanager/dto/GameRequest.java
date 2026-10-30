package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelType;

import java.time.LocalDate;
import java.util.List;

public record GameRequest(
        String title,
        LocalDate gameDate,
        Double timeLimitMinutes,
        List<List<String>> columns,
        List<LevelRequest> levels
) {
    public record LevelRequest(
            String title,
            Double timeLimitMinutes,
            LevelType type,
            Integer hintsCount,
            Integer passThreshold,
            List<HintRequest> hints,
            List<List<String>> associationColumns,
            List<QuestionRequest> questions,
            List<StandardQuestionRequest> standardQuestions
    ) {
    }

    public record HintRequest(
            HintType type,
            String text,
            String imageUrl,
            Double triggerTime,
            Double penaltyTime
    ) {
    }

    public record QuestionRequest(
            String question,
            List<String> answers
    ) {
    }

    public record StandardQuestionRequest(
            String questionText,
            String mediaType,
            String mediaUrl,
            List<String> answers
    ) {
    }
}
