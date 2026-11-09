package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamSessionPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamSessionPresenceRepository extends JpaRepository<TeamSessionPresence, Long> {
    List<TeamSessionPresence> findBySessionIdOrderByLastSeenAtDesc(Long sessionId);

    /** Участники сессии в порядке присоединения — для выбора нового host. */
    List<TeamSessionPresence> findBySessionIdOrderByJoinedAtAsc(Long sessionId);

    Optional<TeamSessionPresence> findBySessionIdAndUserId(Long sessionId, Long userId);
    void deleteBySessionIdAndUserId(Long sessionId, Long userId);
    void deleteBySessionId(Long sessionId);

    /** Убрать присутствие пользователя во всех сессиях. */
    @Modifying
    @Query("delete from TeamSessionPresence p where p.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
