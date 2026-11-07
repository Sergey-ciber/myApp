package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.LevelWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevelWordRepository extends JpaRepository<LevelWord, Long> {
    List<LevelWord> findByLevelIdOrderByColumnNumAscPositionAsc(Long levelId);
}
