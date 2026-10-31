package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelType;

import java.time.LocalDate;
import java.util.List;

public record GameRequest(
        String title,
        LocalDate gameDate,
        Double timeLimitMinutes,
        Integer difficulty,
        String description,
        String visibility,
        List<String> mediaUrls,
        List<List<String>> columns,
        List<LevelRequest> levels
) {
    public record LevelRequest(
            String title,
            Double timeLimitMinutes,
            LevelType type,
            Integer hintsCount,
            List<String> mediaUrls,
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
            Double penaltyTime,
            Long questionId,
            Integer questionIndex,
            List<String> mediaUrls
    ) {
    }

    public record QuestionRequest(
            String question,
            List<String> answers,
            List<String> mediaUrls,
            Integer passThreshold
    ) {
    }

    public record StandardQuestionRequest(
            String questionText,
            String mediaType,
            String mediaUrl,
            List<String> answers,
            List<String> mediaUrls,
            Integer passThreshold
    ) {
    }
}
