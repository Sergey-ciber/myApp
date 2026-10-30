package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Полная игра: название + пирамида слов [16, 8, 4, 2, 1] + автор + лимит времени + уровни. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResponse(Long id, String title, LocalDate gameDate, Instant createdAt, boolean active,
                           Long creatorId, String creatorUsername,
                           Double timeLimitMinutes,
                           String difficulty,
                           List<List<String>> columns,
                           List<LevelResponse> levels) {

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
        User creator = game.getCreator();
        List<LevelResponse> levelResponses = game.getLevels() == null ? List.of() : game.getLevels().stream()
                .sorted(Comparator.comparingInt(GameLevel::getOrderIndex))
                .map(LevelResponse::from)
                .toList();
        return new GameResponse(game.getId(), game.getTitle(), game.getGameDate(), game.getCreatedAt(), game.isActive(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername(),
                game.getTimeLimitMinutes(),
                game.getDifficulty(),
                columns,
                levelResponses);
    }

    public record LevelResponse(
            Long id,
            String title,
            Double timeLimitMinutes,
            LevelType type,
            Integer hintsCount,
            Integer orderIndex,
            Integer passThreshold,
            List<HintResponse> hints,
            List<List<String>> associationColumns,
            List<QuestionResponse> questions,
            List<StandardQuestionResponse> standardQuestions
    ) {
        public static LevelResponse from(GameLevel level) {
            List<HintResponse> hints = level.getHints() == null ? List.of() : level.getHints().stream()
                    .sorted(Comparator.comparingInt(LevelHint::getOrderIndex))
                    .map(HintResponse::from)
                    .toList();
            List<QuestionResponse> questions = new ArrayList<>();
            List<StandardQuestionResponse> standardQuestions = level.getStandardQuestions() == null
                    ? List.of()
                    : level.getStandardQuestions().stream()
                        .sorted(Comparator.comparingInt(StandardQuestion::getOrderIndex))
                        .map(StandardQuestionResponse::from)
                        .toList();
            if (level.getType() == LevelType.NORMAL) {
                // вопросы обычного уровня: правильные ответы — это список вариантов ответа у вопроса
                List<StandardQuestion> source = new ArrayList<>();
                if (level.getStandardQuestions() != null && !level.getStandardQuestions().isEmpty()) {
                    for (StandardQuestion q : level.getStandardQuestions()) {
                        source.add(q);
                    }
                } else if (level.getQuestions() != null) {
                    for (com.demo.taskmanager.entity.LevelQuestion q : level.getQuestions()) {
                        source.add(toStandardQuestion(level, q));
                    }
                }
                for (StandardQuestion q : source) {
                    List<String> answers = new ArrayList<>();
                    if (q.getAnswers() != null) {
                        for (com.demo.taskmanager.entity.StandardAnswer a : q.getAnswers()) {
                            String answerText = a.getAnswerText();
                            if (answerText != null && !answerText.trim().isEmpty()) {
                                answers.add(answerText);
                            }
                        }
                    }
                    questions.add(new QuestionResponse(q.getId(), q.getQuestionText(), answers, q.getOrderIndex()));
                }
            } else {
                questions = level.getQuestions() == null ? List.of() : level.getQuestions().stream()
                        .sorted(Comparator.comparingInt(com.demo.taskmanager.entity.LevelQuestion::getOrderIndex))
                        .map(QuestionResponse::from)
                        .toList();
            }
            List<List<String>> associationColumns = null;
            if (level.getType() == LevelType.ASSOCIATIONS) {
                associationColumns = new ArrayList<>(COLUMN_SIZES.length);
                for (int index = 0; index < COLUMN_SIZES.length; index++) {
                    int columnNum = index + 1;
                    associationColumns.add(level.getWords().stream()
                            .filter(word -> word.getColumnNum() == columnNum)
                            .sorted(Comparator.comparingInt(LevelWord::getPosition))
                            .map(LevelWord::getWord)
                            .toList());
                }
            }
            return new LevelResponse(level.getId(), level.getTitle(), level.getTimeLimitMinutes(), level.getType(),
                    level.getHintsCount(), level.getOrderIndex(), level.getPassThreshold(), hints, associationColumns, questions, standardQuestions);
        }

        private static com.demo.taskmanager.entity.StandardQuestion toStandardQuestion(GameLevel level,
                                                                                          com.demo.taskmanager.entity.LevelQuestion q) {
            com.demo.taskmanager.entity.StandardQuestion standard = new com.demo.taskmanager.entity.StandardQuestion();
            standard.setLevel(level);
            standard.setQuestionText(q.getQuestion());
            standard.setOrderIndex(q.getOrderIndex());
            if (q.getAnswer() != null && !q.getAnswer().trim().isEmpty()) {
                com.demo.taskmanager.entity.StandardAnswer answer = new com.demo.taskmanager.entity.StandardAnswer();
                answer.setQuestion(standard);
                answer.setAnswerText(q.getAnswer());
                standard.getAnswers().add(answer);
            }
            return standard;
        }
    }

    public record HintResponse(Long id, HintType type, String text, String imageUrl, Double triggerTime, Double penaltyTime, Integer orderIndex) {
        public static HintResponse from(com.demo.taskmanager.entity.LevelHint hint) {
            return new HintResponse(hint.getId(), hint.getType(), hint.getText(), hint.getImageUrl(), hint.getTriggerTime(), hint.getPenaltyTime(), hint.getOrderIndex());
        }
    }

    public record QuestionResponse(Long id, String question, List<String> answers, Integer orderIndex) {
        public static QuestionResponse from(com.demo.taskmanager.entity.LevelQuestion question) {
            return new QuestionResponse(question.getId(), question.getQuestion(), question.getAnswer() == null
                    ? List.of() : List.of(question.getAnswer()), question.getOrderIndex());
        }
    }

    public record StandardQuestionResponse(Long id, String questionText, String mediaType, String mediaUrl, Integer orderIndex, List<String> answers) {
        public static StandardQuestionResponse from(StandardQuestion question) {
            List<String> answers = question.getAnswers() == null ? List.of() : question.getAnswers().stream()
                    .map(StandardAnswer::getAnswerText)
                    .filter(a -> a != null && !a.trim().isEmpty())
                    .toList();
            return new StandardQuestionResponse(question.getId(), question.getQuestionText(), question.getMediaType(), question.getMediaUrl(), question.getOrderIndex(), answers);
        }
    }
}
