package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamGameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamGameSessionRepository extends JpaRepository<TeamGameSession, Long> {
    List<TeamGameSession> findByTeamIdOrderByCreatedAtDesc(Long teamId);
    Optional<TeamGameSession> findTopByTeamIdAndGameIdOrderByCreatedAtDesc(Long teamId, Long gameId);
    List<TeamGameSession> findByGameIdAndStatusInOrderByCreatedAtDesc(Long gameId, List<String> statuses);
}
