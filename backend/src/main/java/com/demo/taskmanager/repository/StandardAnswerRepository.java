package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.StandardAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardAnswerRepository extends JpaRepository<StandardAnswer, Long> {
}
