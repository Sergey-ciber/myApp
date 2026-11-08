package com.demo.taskmanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простое ограничение частоты запросов в памяти процесса.
 *
 * Используется для forgot/reset-password. Для нескольких реплик backend
 * счётчики нужно вынести в общее хранилище (Redis), но для текущей
 * однопроцессной конфигурации этого достаточно.
 */
@Service
public class PasswordResetRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(
            @Value("${app.password-reset.rate-limit.max-requests:5}") int maxRequests,
            @Value("${app.password-reset.rate-limit.window-minutes:15}") long windowMinutes) {
        this.maxRequests = Math.max(1, maxRequests);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    /**
     * Регистрирует попытку и возвращает true, если лимит ещё не исчерпан.
     * Ключи независимы: например, отдельно по IP и отдельно по email.
     */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Instant threshold = now.minus(window);
        boolean[] allowed = {false};

        attempts.compute(key, (ignored, queue) -> {
            Deque<Instant> updated = queue == null ? new ArrayDeque<>() : queue;
            while (!updated.isEmpty() && updated.peekFirst().isBefore(threshold)) {
                updated.pollFirst();
            }
            if (updated.size() < maxRequests) {
                updated.addLast(now);
                allowed[0] = true;
            }
            return updated.isEmpty() ? null : updated;
        });

        return allowed[0];
    }
}
