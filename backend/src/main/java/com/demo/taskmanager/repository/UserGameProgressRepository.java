package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.UserGameProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserGameProgressRepository extends JpaRepository<UserGameProgress, Long> {
    Optional<UserGameProgress> findByUserIdAndGameId(Long userId, Long gameId);

    @Modifying
    @Query("delete from UserGameProgress p where p.user.id = :userId and p.game.id = :gameId")
    void deleteByUserIdAndGameId(@Param("userId") Long userId, @Param("gameId") Long gameId);
}
