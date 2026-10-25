package com.demo.taskmanager.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * Запрос на создание/обновление игры.
 * columns — ровно 5 массивов слов: [16, 8, 4, 2, 1].
 * timeLimitMinutes — обязательный лимит времени на прохождение в минутах.
 */
public record GameRequest(String title,
                          @JsonAlias("time_limit") Double timeLimitMinutes,
                          List<List<String>> columns) {
}
