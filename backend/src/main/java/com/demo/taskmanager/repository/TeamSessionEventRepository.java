package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamSessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamSessionEventRepository extends JpaRepository<TeamSessionEvent, Long> {
    List<TeamSessionEvent> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
