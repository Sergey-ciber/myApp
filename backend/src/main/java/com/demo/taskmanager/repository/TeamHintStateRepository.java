package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamHintState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamHintStateRepository extends JpaRepository<TeamHintState, Long> {

    /** Обезличить ссылку на удалённого пользователя, открывшего подсказку. */
    @Modifying
    @Query("update TeamHintState h set h.revealedByUserId = null where h.revealedByUserId = :userId")
    void clearRevealedByUserId(@Param("userId") Long userId);

    Optional<TeamHintState> findBySessionIdAndHintId(Long sessionId, Long hintId);

    List<TeamHintState> findBySessionIdAndLevelId(Long sessionId, Long levelId);

    List<TeamHintState> findBySessionIdAndRevealedTrue(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteByLevelId(Long levelId);
}
