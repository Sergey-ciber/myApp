package com.demo.taskmanager.service;

import com.demo.taskmanager.repository.UserProgressRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Серверный таймер одиночных попыток. Игра живёт независимо от браузера:
 * закрытая вкладка не останавливает общий таймер игры.
 *
 * Каждую секунду берёт активные попытки из БД и передаёт их в
 * {@link GameService#tickSoloAttempt(Long)}. Тот проверяет общий дедлайн игры
 * и таймер уровня под блокировкой строки. Браузер не является источником
 * истины: после перезапуска сервера просроченные попытки завершаются при
 * первой проверке.
 */
@Service
public class SoloGameTimerService {

    private final UserProgressRepository progressRepository;
    private final GameService gameService;

    public SoloGameTimerService(UserProgressRepository progressRepository,
                                GameService gameService) {
        this.progressRepository = progressRepository;
        this.gameService = gameService;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        List<Long> activeIds;
        try {
            activeIds = progressRepository.findActiveAttemptIds();
        } catch (Exception e) {
            // БД временно недоступна — попробуем на следующем тике.
            return;
        }
        for (Long progressId : activeIds) {
            try {
                gameService.tickSoloAttempt(progressId);
            } catch (Exception e) {
                // Ошибка одной попытки не должна останавливать остальные.
            }
        }
    }
}
