package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.User;

import java.time.Instant;

/** Краткая карточка игры для списка: автор, права и лимит времени. */
public record GameSummary(Long id, String title, Instant createdAt, boolean active,
                          Long creatorId, String creatorUsername, double timeLimitMinutes) {

    public static GameSummary from(Game game) {
        User creator = game.getCreator();
        return new GameSummary(game.getId(), game.getTitle(), game.getCreatedAt(), game.isActive(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername(),
                game.getTimeLimitMinutes());
    }
}
