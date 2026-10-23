package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;

import java.time.Instant;

/** Краткая карточка игры для списка сохранённых игр. */
public record GameSummary(Long id, String title, Instant createdAt, boolean active) {

    public static GameSummary from(Game game) {
        return new GameSummary(game.getId(), game.getTitle(), game.getCreatedAt(), game.isActive());
    }
}
