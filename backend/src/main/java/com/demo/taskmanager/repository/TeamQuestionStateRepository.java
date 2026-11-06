package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamQuestionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamQuestionStateRepository extends JpaRepository<TeamQuestionState, Long> {

    Optional<TeamQuestionState> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<TeamQuestionState> findBySessionIdAndLevelId(Long sessionId, Long levelId);

    void deleteBySessionId(Long sessionId);

    void deleteByLevelId(Long levelId);
}
