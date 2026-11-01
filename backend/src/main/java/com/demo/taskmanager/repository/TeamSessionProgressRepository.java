package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamSessionProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamSessionProgressRepository extends JpaRepository<TeamSessionProgress, Long> {
    Optional<TeamSessionProgress> findBySessionIdAndLevelId(Long sessionId, Long levelId);
}
