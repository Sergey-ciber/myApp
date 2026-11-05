package com.demo.taskmanager.service;

import com.demo.taskmanager.entity.TeamGameSession;
import com.demo.taskmanager.repository.TeamGameSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Серверный таймер командных игр. Игра живёт независимо от подключений игроков.
 *
 * Каждую секунду берёт активные сессии из БД и передаёт их в
 * {@link TeamGameService#tickSession(Long)}. Тот проверяет ends_at и таймеры
 * уровней под блокировкой строки. Браузер не является источником истины:
 * после перезапуска сервера просроченные партии завершаются при первой проверке.
 */
@Service
public class TeamGameTimerService {

    private final TeamGameSessionRepository sessionRepository;
    private final TeamGameService teamGameService;

    public TeamGameTimerService(TeamGameSessionRepository sessionRepository,
                                TeamGameService teamGameService) {
        this.sessionRepository = sessionRepository;
        this.teamGameService = teamGameService;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        List<Long> activeIds;
        try {
            activeIds = sessionRepository.findActiveSessions().stream()
                    .map(TeamGameSession::getId)
                    .toList();
        } catch (Exception e) {
            // БД временно недоступна — попробуем на следующем тике.
            return;
        }
        for (Long sessionId : activeIds) {
            try {
                teamGameService.tickSession(sessionId);
            } catch (Exception e) {
                // Ошибка одной сессии не должна останавливать остальные.
            }
        }
    }
}
