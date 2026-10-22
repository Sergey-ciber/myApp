package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Board;
import com.demo.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByBoardOrderByPositionAsc(Board board);
    long countByBoard(Board board);
}
