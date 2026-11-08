package com.demo.taskmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Включает асинхронное выполнение задач.
 *
 * Используется для отправки писем восстановления пароля: HTTP-запрос не должен
 * ждать SMTP, особенно если почтовый сервер отвечает медленно или недоступен.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Отдельный пул для писем. Имя фиксировано, чтобы @Async не выбирал
     * случайный TaskExecutor из контекста (у WebSocket их несколько).
     */
    @Bean(name = "mailTaskExecutor")
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }
}
