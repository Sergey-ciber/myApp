package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.GameWord;
import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelHint;
import com.demo.taskmanager.entity.LevelType;
import com.demo.taskmanager.entity.LevelWord;
import com.demo.taskmanager.entity.StandardQuestion;
import com.demo.taskmanager.entity.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Игра для прохождения: только стартовые слова колонки 1 и лимит времени.
 * Ответы колонок 2-5 на клиент не попадает — они проверяются на сервере.
 * Для уровня типа «Обычный» отдаются только сам вопрос (без правильных ответов),
 * порог прохождения и подсказки.
 */
public record GamePlayResponse(Long id, String title, Instant createdAt, boolean active,
                               Long creatorId, String creatorUsername,
                               double timeLimitMinutes, List<String> startWords,
                               Long levelId, LevelType levelType, Integer passThreshold,
                               List<GameQuestionViewResponse> questions,
                               List<Double> hintTriggerMinutes,
                               List<GameLevelView> levels) {

    public GamePlayResponse(Long id, String title, Instant createdAt, boolean active,
                            Long creatorId, String creatorUsername,
                            double timeLimitMinutes, List<String> startWords) {
        this(id, title, createdAt, active, creatorId, creatorUsername, timeLimitMinutes, startWords, null, null, null, List.of(), List.of(), List.of());
    }

    public static GamePlayResponse from(Game game) {
        List<String> startWords = game.getWords().stream()
                .filter(word -> word.getColumnNum() == 1)
                .sorted(Comparator.comparingInt(GameWord::getPosition))
                .map(GameWord::getWord)
                .toList();
        User creator = game.getCreator();
        GameLevel level = currentLevel(game);
        List<GameQuestionViewResponse> questions = level == null ? List.of() : questionsOf(level);
        List<Double> hintTriggerMinutes = level == null || level.getHints() == null
                ? List.of()
                : level.getHints().stream()
                    .sorted(Comparator.comparingInt(LevelHint::getOrderIndex))
                    .map(LevelHint::getTriggerTime)
                    .toList();
        List<GameLevelView> levels = game.getLevels() == null
                ? List.of()
                : game.getLevels().stream()
                    .filter(l -> l != null)
                    .sorted(Comparator.comparingInt(GameLevel::getOrderIndex))
                    .map(GameLevelView::from)
                    .toList();
        return new GamePlayResponse(game.getId(), game.getTitle(), game.getCreatedAt(), game.isActive(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername(),
                game.getTimeLimitMinutes(),
                startWords,
                level == null ? null : level.getId(),
                level == null ? null : level.getType(),
                level == null ? null : level.getPassThreshold(),
                questions,
                hintTriggerMinutes,
                levels);
    }

    /** Текущий уровень игры: первый по порядку (обычно это и есть единственный уровень). */
    private static GameLevel currentLevel(Game game) {
        return game.getLevels() == null ? null : game.getLevels().stream()
                .filter(level -> level != null)
                .sorted(Comparator.comparingInt(GameLevel::getOrderIndex))
                .findFirst()
                .orElse(null);
    }

    /** Вопросы уровня для игрока: без правильных ответов, только текст и медиа. */
    private static List<GameQuestionViewResponse> questionsOf(GameLevel level) {
        List<GameQuestionViewResponse> result = new ArrayList<>();
        if (level.getStandardQuestions() != null) {
            for (StandardQuestion q : level.getStandardQuestions()) {
                result.add(GameQuestionViewResponse.from(q));
            }
        } else if (level.getQuestions() != null) {
            for (com.demo.taskmanager.entity.LevelQuestion q : level.getQuestions()) {
                result.add(new GameQuestionViewResponse(q.getId(), q.getQuestion(), null, null, q.getOrderIndex()));
            }
        }
        return result;
    }

    public record GameQuestionViewResponse(Long id, String questionText, String mediaType, String mediaUrl, Integer orderIndex) {
        public static GameQuestionViewResponse from(StandardQuestion question) {
            return new GameQuestionViewResponse(question.getId(), question.getQuestionText(), question.getMediaType(),
                    question.getMediaUrl(), question.getOrderIndex());
        }
    }

    /** Полное описание уровня для игрока (без правильных ответов). */
    public record GameLevelView(Long id, String title, LevelType type, double timeLimitMinutes, Integer passThreshold,
                                List<GameHintView> hints, List<GameQuestionViewResponse> questions, List<String> startWords) {
        public static GameLevelView from(GameLevel level) {
            List<GameHintView> hints = level.getHints() == null
                    ? List.of()
                    : level.getHints().stream()
                        .sorted(Comparator.comparingInt(LevelHint::getOrderIndex))
                        .map(GameHintView::from)
                        .toList();
            List<GameQuestionViewResponse> questions = questionsOf(level);
            List<String> startWords = level.getType() == LevelType.ASSOCIATIONS && level.getWords() != null
                    ? level.getWords().stream()
                        .filter(w -> w.getColumnNum() == 1)
                        .sorted(Comparator.comparingInt(LevelWord::getPosition))
                        .map(LevelWord::getWord)
                        .toList()
                    : List.of();
            return new GameLevelView(level.getId(), level.getTitle(), level.getType(), level.getTimeLimitMinutes(),
                    level.getPassThreshold(), hints, questions, startWords);
        }
    }

    public record GameHintView(Long id, HintType type, String text, String imageUrl, Double triggerTime, Double penaltyTime, Integer orderIndex) {
        public static GameHintView from(LevelHint hint) {
            return new GameHintView(hint.getId(), hint.getType(), hint.getText(), hint.getImageUrl(), hint.getTriggerTime(),
                    hint.getPenaltyTime(), hint.getOrderIndex());
        }
    }
}
