package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByOrderByCreatedAtDesc();

    Optional<Game> findFirstByActiveTrue();

    /** Передать все игры удаляемого пользователя другому владельцу. */
    @Modifying
    @Query("update Game g set g.creator = :newCreator where g.creator.id = :userId")
    int reassignCreator(@Param("userId") Long userId, @Param("newCreator") User newCreator);
}
