package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamSessionPresence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamSessionPresenceRepository extends JpaRepository<TeamSessionPresence, Long> {
    List<TeamSessionPresence> findBySessionIdOrderByLastSeenAtDesc(Long sessionId);
    Optional<TeamSessionPresence> findBySessionIdAndUserId(Long sessionId, Long userId);
    void deleteBySessionIdAndUserId(Long sessionId, Long userId);
}
