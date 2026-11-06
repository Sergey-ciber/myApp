package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamHintState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamHintStateRepository extends JpaRepository<TeamHintState, Long> {

    Optional<TeamHintState> findBySessionIdAndHintId(Long sessionId, Long hintId);

    List<TeamHintState> findBySessionIdAndLevelId(Long sessionId, Long levelId);

    List<TeamHintState> findBySessionIdAndRevealedTrue(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteByLevelId(Long levelId);
}
