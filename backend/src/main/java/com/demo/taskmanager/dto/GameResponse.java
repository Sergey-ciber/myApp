package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameWord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Полная игра: название + пирамида слов [16, 8, 4, 2, 1]. */
public record GameResponse(Long id, String title, Instant createdAt, boolean active,
                           List<List<String>> columns) {

    /** Размеры колонок пирамиды (всего 31 слово). */
    public static final int[] COLUMN_SIZES = {16, 8, 4, 2, 1};

    public static GameResponse from(Game game) {
        List<List<String>> columns = new ArrayList<>(COLUMN_SIZES.length);
        for (int index = 0; index < COLUMN_SIZES.length; index++) {
            int columnNum = index + 1;
            columns.add(game.getWords().stream()
                    .filter(word -> word.getColumnNum() == columnNum)
                    .sorted(Comparator.comparingInt(GameWord::getPosition))
                    .map(GameWord::getWord)
                    .toList());
        }
        return new GameResponse(game.getId(), game.getTitle(), game.getCreatedAt(),
                game.isActive(), columns);
    }
}
