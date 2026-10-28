package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.GameLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameLevelRepository extends JpaRepository<GameLevel, Long> {
}
