package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.GameResult;

import java.time.Instant;

/** Сохранённый результат прохождения. */
public record ResultResponse(Long id, int timeSeconds, Instant completedAt) {

    public static ResultResponse from(GameResult result) {
        return new ResultResponse(result.getId(), result.getTimeSeconds(), result.getCompletedAt());
    }
}
