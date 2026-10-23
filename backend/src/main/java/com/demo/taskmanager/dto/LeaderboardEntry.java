package com.demo.taskmanager.dto;

import java.time.Instant;

/**
 * Строка рейтинга игры: место, логин, лучшее время и когда оно было показано.
 * В рейтинг попадает только лучшая попытка каждого пользователя.
 */
public record LeaderboardEntry(int place, Long userId, String username,
                               int timeSeconds, Instant completedAt) {
}
