package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameWord;
import com.demo.taskmanager.entity.User;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Игра для прохождения: только стартовые слова колонки 1.
 * Ответы колонок 2-5 на клиент не попадают — они проверяются на сервере.
 */
public record GamePlayResponse(Long id, String title, Instant createdAt, boolean active,
                               Long creatorId, String creatorUsername, List<String> startWords) {

    public static GamePlayResponse from(Game game) {
        List<String> startWords = game.getWords().stream()
                .filter(word -> word.getColumnNum() == 1)
                .sorted(Comparator.comparingInt(GameWord::getPosition))
                .map(GameWord::getWord)
                .toList();
        User creator = game.getCreator();
        return new GamePlayResponse(game.getId(), game.getTitle(), game.getCreatedAt(), game.isActive(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername(),
                startWords);
    }
}
