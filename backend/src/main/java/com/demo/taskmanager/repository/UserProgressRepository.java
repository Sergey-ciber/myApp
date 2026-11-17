package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.UserProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    /** Прогресс ищется строго по паре «пользователь + игра» — чужие данные недоступны. */
    Optional<UserProgress> findByGameIdAndUserId(Long gameId, Long userId);

    /**
     * Блокировка строки прогресса на время транзакции. Нужна, чтобы серверный
     * таймер и действия игрока (ответ, старт уровня, результат) не обрабатывали
     * одну и ту же попытку параллельно.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProgress p where p.game.id = :gameId and p.user.id = :userId")
    Optional<UserProgress> findByGameIdAndUserIdForUpdate(@Param("gameId") Long gameId,
                                                          @Param("userId") Long userId);

    /** Блокировка попытки по id — для серверного таймера. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProgress p where p.id = :id")
    Optional<UserProgress> findByIdForUpdate(@Param("id") Long id);

    /**
     * Активные одиночные попытки: не завершены ни победой (finished_seconds),
     * ни таймаутом (finish_reason). Серверный таймер проверяет только их.
     */
    @Query("select p.id from UserProgress p "
            + "where p.finishedSeconds is null and p.finishReason is null")
    List<Long> findActiveAttemptIds();

    /** Удалить весь прогресс пользователя во всех играх. */
    @Modifying
    @Query("delete from UserProgress p where p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

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
