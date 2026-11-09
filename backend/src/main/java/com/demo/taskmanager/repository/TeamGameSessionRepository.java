package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamGameSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamGameSessionRepository extends JpaRepository<TeamGameSession, Long> {
    List<TeamGameSession> findByTeamIdOrderByCreatedAtDesc(Long teamId);
    List<TeamGameSession> findByGameId(Long gameId);
    Optional<TeamGameSession> findTopByTeamIdAndGameIdOrderByCreatedAtDesc(Long teamId, Long gameId);

    /** Активные сессии команды — их показываем в списке игр как «Активные игры команды». */
    List<TeamGameSession> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, String status);

    /** Активная партия конкретной команды по конкретной игре. */
    Optional<TeamGameSession> findTopByTeamIdAndGameIdAndStatusOrderByCreatedAtDesc(Long teamId, Long gameId, String status);

    /** Активные сессии всех команд, в которых состоит пользователь. */
    List<TeamGameSession> findByTeamIdInAndStatusOrderByCreatedAtDesc(List<Long> teamIds, String status);

    List<TeamGameSession> findByTeamIdInAndStatusInOrderByCreatedAtDesc(List<Long> teamIds, List<String> statuses);

    List<TeamGameSession> findByGameIdAndStatusInOrderByCreatedAtDesc(Long gameId, List<String> statuses);

    /**
     * Активные сессии для фонового таймера. Учитываем и старый формат статуса
     * (ACTIVE), чтобы после обновления приложения корректно завершались партии,
     * созданные до миграции.
     */
    @Query("select s from TeamGameSession s where upper(s.status) = 'ACTIVE'")
    List<TeamGameSession> findActiveSessions();

    /**
     * Блокировка строки сессии на время завершения: защищает от гонок
     * между ручным завершением хоста и серверным таймером.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TeamGameSession s where s.id = :id")
    Optional<TeamGameSession> findByIdForUpdate(@Param("id") Long id);
}
