package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByOrderByCreatedAtDesc();

    Optional<Game> findFirstByActiveTrue();
}
