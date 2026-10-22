package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Board;
import java.time.Instant;

public record BoardResponse(Long id, String title, String description, String color,
                            Instant createdAt, long taskCount) {

    public static BoardResponse from(Board board, long taskCount) {
        return new BoardResponse(board.getId(), board.getTitle(), board.getDescription(),
                board.getColor(), board.getCreatedAt(), taskCount);
    }
}
