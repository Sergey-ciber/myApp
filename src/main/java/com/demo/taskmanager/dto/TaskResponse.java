package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Task;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(Long id, String title, String description, String status,
                           Integer position, LocalDate dueDate, Long boardId,
                           Instant createdAt, Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getStatus().name(), task.getPosition(), task.getDueDate(),
                task.getBoard().getId(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
