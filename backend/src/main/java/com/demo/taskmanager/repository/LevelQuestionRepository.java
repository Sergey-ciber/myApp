package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.LevelQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelQuestionRepository extends JpaRepository<LevelQuestion, Long> {
}
