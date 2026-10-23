package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    /** Все попытки игры: лучшие сверху, при равенстве — кто показал время раньше. */
    @EntityGraph(attributePaths = "user")
    List<GameResult> findByGameIdOrderByTimeSecondsAscCompletedAtAsc(Long gameId);
}
