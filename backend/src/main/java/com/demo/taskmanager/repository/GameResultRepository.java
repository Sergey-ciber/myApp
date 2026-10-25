package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    /** Все попытки игры: лучшие сверху, при равенстве — кто показал время раньше. */
    @EntityGraph(attributePaths = "user")
    List<GameResult> findByGameIdOrderByTimeSecondsAscCompletedAtAsc(Long gameId);

    /**
     * Количество уникальных игр, сохранённых участниками команды.
     * Повторная отправка результата одной и той же игры не увеличивает показатель.
     */
    @Query("select count(r) from GameResult r where r.id in (" +
            "select min(r2.id) from GameResult r2 where r2.user.id in :userIds " +
            "group by r2.user.id, r2.game.id)")
    long countMemberGames(@Param("userIds") Collection<Long> userIds);
}
