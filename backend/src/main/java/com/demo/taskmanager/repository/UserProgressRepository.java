package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    /** Прогресс ищется строго по паре «пользователь + игра» — чужие данные недоступны. */
    Optional<UserProgress> findByGameIdAndUserId(Long gameId, Long userId);

    /**
     * Создаёт пустой прогресс, если его ещё нет.
     * ON CONFLICT защищает от гонки двух одновременных открытий игры.
     */
    @Modifying
    @Query(value = "insert into user_progress (user_id, game_id, solved_words, hint_level, hint_prefixes, started_at, updated_at) "
            + "values (:userId, :gameId, '{}', 0, '{}', now(), now()) on conflict (user_id, game_id) do nothing",
            nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("gameId") Long gameId);
}
