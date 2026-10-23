package com.demo.taskmanager.dto;

import java.util.List;

/**
 * Запрос на создание/обновление игры.
 * columns — ровно 5 массивов слов: [16, 8, 4, 2, 1].
 */
public record GameRequest(String title, List<List<String>> columns) {
}
