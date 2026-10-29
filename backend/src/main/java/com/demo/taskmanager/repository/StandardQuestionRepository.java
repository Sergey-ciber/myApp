package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.StandardQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardQuestionRepository extends JpaRepository<StandardQuestion, Long> {
}
