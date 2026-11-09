package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamQuestionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamQuestionStateRepository extends JpaRepository<TeamQuestionState, Long> {

    /** Обезличить ссылку на удалённого пользователя, решившего вопрос. */
    @Modifying
    @Query("update TeamQuestionState q set q.solvedByUserId = null where q.solvedByUserId = :userId")
    void clearSolvedByUserId(@Param("userId") Long userId);

    Optional<TeamQuestionState> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<TeamQuestionState> findBySessionIdAndLevelId(Long sessionId, Long levelId);

    void deleteBySessionId(Long sessionId);

    void deleteByLevelId(Long levelId);
}
