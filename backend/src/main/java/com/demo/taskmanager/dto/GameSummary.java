package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.User;

import java.time.Instant;
import java.time.LocalDate;

/** Краткая карточка игры для списка: автор, права и лимит времени. */
public record GameSummary(Long id, String title, Instant createdAt, boolean active,
                          Long creatorId, String creatorUsername, double timeLimitMinutes,
                          LocalDate gameDate, int levelsCount) {

    public static GameSummary from(Game game) {
        User creator = game.getCreator();
        int levelsCount = game.getLevels() == null ? 0 : game.getLevels().size();
        double timeLimit = game.getTimeLimitMinutes() == null ? 10.0 : game.getTimeLimitMinutes();
        return new GameSummary(game.getId(), game.getTitle(), game.getCreatedAt(), game.isActive(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername(),
                timeLimit,
                game.getGameDate(),
                levelsCount);
    }
}
