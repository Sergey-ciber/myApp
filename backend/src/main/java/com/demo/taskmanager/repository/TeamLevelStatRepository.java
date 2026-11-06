package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamLevelStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamLevelStatRepository extends JpaRepository<TeamLevelStat, Long> {

    List<TeamLevelStat> findBySessionIdOrderByLevelIndexAsc(Long sessionId);

    Optional<TeamLevelStat> findBySessionIdAndLevelId(Long sessionId, Long levelId);

    void deleteBySessionId(Long sessionId);
}
