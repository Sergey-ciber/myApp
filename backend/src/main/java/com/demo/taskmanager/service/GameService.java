package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.CheckAnswerRequest;
import com.demo.taskmanager.dto.CheckAnswerResponse;
import com.demo.taskmanager.dto.GamePlayResponse;
import com.demo.taskmanager.dto.GameProgressDto;
import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.GameSessionResponse;
import com.demo.taskmanager.dto.GameSummary;
import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.QuestionHintRequest;
import com.demo.taskmanager.dto.QuestionHintResponse;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.dto.TimeoutResponse;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.GameResult;
import com.demo.taskmanager.entity.GameWord;
import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelHint;
import com.demo.taskmanager.entity.LevelQuestion;
import com.demo.taskmanager.entity.LevelWord;
import com.demo.taskmanager.entity.StandardAnswer;
import com.demo.taskmanager.entity.StandardQuestion;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.entity.UserProgress;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.GameResultRepository;
import com.demo.taskmanager.repository.StandardQuestionRepository;
import com.demo.taskmanager.repository.UserProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GameService {

    /** Размеры колонок пирамиды: 16 + 8 + 4 + 2 + 1 = 31 слово. */
    private static final int[] COLUMN_SIZES = GameResponse.COLUMN_SIZES;
    private static final int TOTAL_WORDS = 31;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_WORD_LENGTH = 60;
    /** Больше суток на одну игру не отводим — защита от мусорных результатов. */
    private static final int MAX_TIME_SECONDS = 24 * 60 * 60;
    /** Лимит времени на прохождение задаёт администратор: от 0 до 24 часов. */
    private static final double MAX_TIME_LIMIT_MINUTES = 24 * 60;
    /** Подсказки открываются по серверному таймеру: 3, 6, 9, 12 минут после старта. */
    private static final int HINT_LEVEL_ONE_SECONDS = 180;
    private static final int HINT_LEVEL_TWO_SECONDS = 360;
    private static final int HINT_LEVEL_THREE_SECONDS = 540;
    private static final int HINT_LEVEL_FOUR_SECONDS = 720;
    private static final int MAX_HINT_LEVEL = 4;

    private final GameRepository gameRepository;
    private final GameResultRepository resultRepository;
    private final UserProgressRepository progressRepository;
    private final TeamService teamService;
    private final ObjectMapper objectMapper;
    private final StandardQuestionRepository standardQuestionRepository;

    public GameService(GameRepository gameRepository,
                       GameResultRepository resultRepository,
                       UserProgressRepository progressRepository,
                       TeamService teamService,
                       ObjectMapper objectMapper,
                       StandardQuestionRepository standardQuestionRepository) {
        this.gameRepository = gameRepository;
        this.resultRepository = resultRepository;
        this.progressRepository = progressRepository;
        this.teamService = teamService;
        this.objectMapper = objectMapper;
        this.standardQuestionRepository = standardQuestionRepository;
    }

    @Transactional(readOnly = true)
    public List<GameSummary> listGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(GameSummary::from)
                .toList();
    }

    /**
     * Игра для прохождения: только стартовые слова колонки 1
     * и прогресс текущего пользователя. Чужие ответы не отдаются.
     */
    @Transactional(readOnly = true)
    public GameSessionResponse getGameForPlay(Long id, User user) {
        Game game = findGame(id);
        UserProgress progress = progressRepository.findByGameIdAndUserId(id, user.getId()).orElse(null);
        return new GameSessionResponse(GamePlayResponse.from(game),
                progress == null ? null : toDto(progress));
    }

    /** Полная игра со всеми словами — только для формы редактирования её автора. */
    @Transactional(readOnly = true)
    public GameResponse getGameForEdit(Long id, User user) {
        Game game = findGame(id);
        requireCreator(game, user);
        if (game.getMediaUrls() != null) game.getMediaUrls().size();
        if (game.getLevels() != null) {
            game.getLevels().forEach(level -> {
                if (level.getMediaUrls() != null) level.getMediaUrls().size();
                if (level.getHints() != null) {
                    level.getHints().forEach(hint -> {
                        if (hint.getMediaUrls() != null) hint.getMediaUrls().size();
                        if (hint.getQuestion() != null) {
                            hint.getQuestion().getId();
                            if (hint.getQuestion().getMediaUrls() != null) hint.getQuestion().getMediaUrls().size();
                            if (hint.getQuestion().getAnswers() != null) hint.getQuestion().getAnswers().size();
                        }
                    });
                }
                if (level.getStandardQuestions() != null) {
                    level.getStandardQuestions().forEach(q -> {
                        if (q.getAnswers() != null) q.getAnswers().size();
                        if (q.getMediaUrls() != null) q.getMediaUrls().size();
                    });
                }
                if (level.getQuestions() != null) level.getQuestions().size();
                if (level.getWords() != null) level.getWords().size();
            });
        }
        return GameResponse.from(game);
    }

    /**
     * Старт или продолжение попытки: если прогресса нет — создаём его
     * для текущего пользователя, иначе возвращаем сохранённый.
     */
    @Transactional
    public GameSessionResponse startSession(Long id, User user) {
        Game game = findGame(id);
        UserProgress progress = findOrCreateProgress(game, user);
        return new GameSessionResponse(GamePlayResponse.from(game), toDto(progress));
    }

    /**
     * Проверка ответа на сервере: правильные слова не уходят на клиент.
     * Верный ответ сразу сохраняется в прогресс этого пользователя.
     */
    @Transactional
    public CheckAnswerResponse checkAnswer(Long gameId, User user, CheckAnswerRequest request) {
        Game game = findGame(gameId);
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        // уровень обычный: проверяем текст ответа против всех вариантов ответа вопроса
        if (request.questionId() != null) {
            return checkStandardAnswer(game, user, request);
        }
        if (request.column() == null || request.position() == null) {
            throw new BadRequestException("Укажите колонку и номер ответа");
        }
        int column = request.column();
        int position = request.position();
        if (column < 2 || column > COLUMN_SIZES.length) {
            throw new BadRequestException("Проверять можно только ответы колонок 2-5");
        }
        if (position < 1 || position > COLUMN_SIZES[column - 1]) {
            throw new BadRequestException("В колонке " + column + " нет ответа " + position);
        }

        String expected = wordAt(game, column, position);
        boolean correct = expected != null
                && normalize(request.answer()).equals(normalize(expected));

        UserProgress progress = findOrCreateProgress(game, user);
        if (correct) {
            Map<String, String> solved = parseSolved(progress);
            solved.put(column + ":" + position, expected);
            writeSolved(progress, solved);
            // ячейка разгадана — подсказка для неё больше не нужна
            Map<String, String> prefixes = parsePrefixes(progress);
            if (prefixes.remove(column + ":" + position) != null) {
                writePrefixes(progress, prefixes);
            }
            progress.setUpdatedAt(Instant.now());
            progressRepository.save(progress);
        }
        return new CheckAnswerResponse(correct, toDto(progress));
    }

    /**
     * Проверка вопроса обычного уровня: введённый ответ сравнивается со ВСЕМИ вариантами
     * ответа вопроса (регистр и пробелы по краям не учитываются). Совпадение с любым
     * вариантом засчитывается как правильный ответ.
     */
    private CheckAnswerResponse checkStandardAnswer(Game game, User user, CheckAnswerRequest request) {
        StandardQuestion question = standardQuestionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        if (question.getLevel() == null || question.getLevel().getGame() == null
                || !question.getLevel().getGame().getId().equals(game.getId())) {
            throw new NotFoundException("Вопрос не найден");
        }
        UserProgress progress = findOrCreateProgress(game, user);

        String normalizedAnswer = normalize(request.answer());
        StandardAnswer matchedAnswer = null;
        if (question.getAnswers() != null) {
            for (StandardAnswer a : question.getAnswers()) {
                String text = a.getAnswerText() == null ? "" : a.getAnswerText().trim();
                if (!text.isEmpty() && normalize(text).equals(normalizedAnswer)) {
                    matchedAnswer = a;
                    break;
                }
            }
        }
        boolean correct = matchedAnswer != null;

        if (correct) {
            // Каждый разгаданный вариант ответа хранится отдельным ключом,
            // чтобы можно было считать, сколько вариантов уже дано по вопросу.
            String key = "l" + question.getLevel().getId() + ":q" + question.getId() + ":a" + matchedAnswer.getId();
            Map<String, String> solved = parseSolved(progress);
            solved.put(key, matchedAnswer.getAnswerText());
            writeSolved(progress, solved);
            Map<String, String> prefixes = parsePrefixes(progress);
            if (prefixes.remove(key) != null) {
                writePrefixes(progress, prefixes);
            }
            progress.setUpdatedAt(Instant.now());
            progressRepository.save(progress);
        }
        return new CheckAnswerResponse(correct, toDto(progress));
    }

    /** Сколько вопросов обычного уровня уже разгадано данным игроком. */
    private int solvedCountForLevel(UserProgress progress, Long levelId) {
        Map<String, String> solved = parseSolved(progress);
        String prefix = "l" + levelId + ":";
        return (int) solved.keySet().stream().filter(key -> key != null && key.startsWith(prefix)).count();
    }

    /**
     * Раскрыть содержимое подсказки конкретного вопроса обычного уровня.
     * Для «Первая буква» возвращает первую букву правильного ответа, для «Общая подсказка» — текст/медиа.
     */
    @Transactional
    public QuestionHintResponse revealQuestionHint(Long gameId, User user, QuestionHintRequest request) {
        Game game = findGame(gameId);
        if (request == null || request.questionId() == null) {
            throw new BadRequestException("Укажите вопрос");
        }
        StandardQuestion question = standardQuestionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        if (question.getLevel() == null || question.getLevel().getGame() == null
                || !question.getLevel().getGame().getId().equals(game.getId())) {
            throw new NotFoundException("Вопрос не найден");
        }
        GameLevel level = question.getLevel();
        List<LevelHint> hints = level.getHints() == null
                ? List.of()
                : level.getHints().stream().sorted(Comparator.comparingInt(LevelHint::getOrderIndex)).toList();
        int hintIndex = request.hintIndex() == null ? 0 : request.hintIndex();
        if (hintIndex < 0 || hintIndex >= hints.size()) {
            throw new BadRequestException("Подсказка не найдена");
        }
        LevelHint hint = hints.get(hintIndex);
        String firstLetter = null;
        if (hint.getType() == HintType.FIRST_LETTER) {
            firstLetter = question.getAnswers() == null ? null : question.getAnswers().stream()
                    .map(StandardAnswer::getAnswerText)
                    .filter(a -> a != null && !a.trim().isEmpty())
                    .findFirst()
                    .map(a -> a.trim().substring(0, 1))
                    .orElse(null);
        }
        return new QuestionHintResponse(firstLetter, hint.getText(), hint.getImageUrl());
    }

    /**
     * Выдать следующую подсказку. Доступность считается по серверному времени:
     * 180, 360, 540 и 720 секунд после старта. Подсказка открывает первые буквы
     * только доступных (родители разгаданы) неразгаданных ячеек.
     */
    @Transactional
    public GameProgressDto takeHint(Long gameId, User user) {
        Game game = findGame(gameId);
        UserProgress progress = findOrCreateProgress(game, user);

        GameLevel level = currentLevel(game);
        List<LevelHint> levelHints = level.getHints() == null
                ? List.of()
                : level.getHints().stream()
                    .sorted(Comparator.comparingInt(LevelHint::getOrderIndex))
                    .toList();

        int hintLevel = progress.getHintLevel();
        if (hintLevel >= levelHints.size()) {
            throw new BadRequestException("Все подсказки уже получены");
        }

        LevelHint nextHint = levelHints.get(hintLevel);
        long requiredSeconds = Math.round(
                (nextHint.getTriggerTime() == null ? 0 : nextHint.getTriggerTime()) * 60);

        long elapsed = elapsedSeconds(progress);
        if (elapsed < requiredSeconds) {
            throw new BadRequestException("Подсказка будет доступна через "
                    + (requiredSeconds - elapsed) + " с");
        }

        int newLevel = hintLevel + 1;
        Map<String, String> solved = parseSolved(progress);
        // Найдём доступные поля: колонка >=2, не разгадано, оба родителя из предыдущей колонки разгаданы
        java.util.List<GameWord> availableWords = new java.util.ArrayList<>();
        for (GameWord word : game.getWords()) {
            int columnNum = word.getColumnNum();
            if (columnNum < 2) {
                continue;
            }
            int position = word.getPosition();
            String key = columnNum + ":" + position;
            if (solved.containsKey(key)) {
                continue;
            }
            if (columnNum == 2) {
                // для второй колонки родители - пара из колонки 1, всегда доступны если не разгаданы
                availableWords.add(word);
                continue;
            }
            int parentColumn = columnNum - 1;
            int parentPos1 = (position - 1) * 2 + 1;
            int parentPos2 = parentPos1 + 1;
            String p1 = parentColumn + ":" + parentPos1;
            String p2 = parentColumn + ":" + parentPos2;
            if (solved.containsKey(p1) && solved.containsKey(p2)) {
                availableWords.add(word);
            }
        }
        if (availableWords.isEmpty()) {
            throw new BadRequestException("Сначала разгадайте хотя бы одну пару слов, чтобы использовать подсказку");
        }

        Map<String, String> prefixes = new LinkedHashMap<>();
        for (GameWord word : availableWords) {
            String value = word.getWord();
            if (value == null || value.isEmpty()) {
                continue;
            }
            String key = word.getColumnNum() + ":" + word.getPosition();
            int prefixLen = Math.min(newLevel, value.length());
            if (prefixLen <= 0) {
                continue;
            }
            prefixes.put(key, value.substring(0, prefixLen));
        }
        if (prefixes.isEmpty()) {
            throw new BadRequestException("Сначала разгадайте хотя бы одну пару слов, чтобы использовать подсказку");
        }
        progress.setHintLevel(newLevel);
        writePrefixes(progress, prefixes);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);
        return toDto(progress);
    }

    /** «Начать заново»: очищаем только прогресс этого пользователя. */
    @Transactional
    public GameProgressDto restartProgress(Long gameId, User user) {
        Game game = findGame(gameId);
        UserProgress progress = findOrCreateProgress(game, user);
        progress.setSolvedWords("{}");
        progress.setHintLevel(0);
        progress.setHintPrefixes("{}");
        progress.setStartedAt(Instant.now());
        progress.setFinishedSeconds(null);
        progress.setWinCounted(false);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);
        return toDto(progress);
    }

    @Transactional
    public GameResponse createGame(GameRequest request, User creator) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestException("Укажите название игры");
        }
        if (title.length() > 150) {
            throw new BadRequestException("Название игры не должно быть длиннее 150 символов");
        }

        Game game = new Game();
        game.setTitle(title);
        game.setGameDate(request.gameDate());
        game.setDescription(request.description() == null ? null : request.description().trim());
        game.setVisibility(normalizeVisibility(request.visibility()));
        game.setMediaUrls(normalizeMediaUrls(request.mediaUrls()));
        Double timeLimit = request.timeLimitMinutes() == null ? 10.0 : validateTimeLimit(request.timeLimitMinutes());
        game.setTimeLimitMinutes(timeLimit);
        game.setDifficulty(normalizeDifficulty(request.difficulty()));
        game.setCreator(creator);
        game.setActive(gameRepository.count() == 0);

        boolean hasAssociationLevel = request.levels() != null && request.levels().stream().anyMatch(l -> l != null && l.type() == com.demo.taskmanager.entity.LevelType.ASSOCIATIONS);
        if (hasAssociationLevel && request.columns() != null && !request.columns().isEmpty()) {
            List<List<String>> columns = validateAndNormalizeColumns(request.columns());
            fillWords(game, columns);
        } else if (hasAssociationLevel && (request.columns() == null || request.columns().isEmpty())) {
            throw new BadRequestException("Для уровня типа «Игра в ассоциации» необходимо заполнить пирамиду из 31 слова");
        }

        if (request.levels() != null && !request.levels().isEmpty()) {
            for (int i = 0; i < request.levels().size(); i++) {
                GameRequest.LevelRequest lr = request.levels().get(i);
                if (lr == null) {
                    throw new BadRequestException("Пустой уровень");
                }
                String levelTitle = lr.title() == null ? "" : lr.title().trim();
                if (levelTitle.isEmpty()) {
                    throw new BadRequestException("Укажите название уровня");
                }
                if (lr.type() == null) {
                    throw new BadRequestException("Укажите тип уровня");
                }
                GameLevel level = new GameLevel();
                level.setGame(game);
                level.setTitle(levelTitle);
                double levelTime = lr.timeLimitMinutes() == null ? timeLimit : validateTimeLimit(lr.timeLimitMinutes());
                level.setTimeLimitMinutes(levelTime);
                level.setType(lr.type());
                level.setHintsCount(lr.hintsCount() == null ? 0 : lr.hintsCount());
                level.setOrderIndex(i);
                level.setMediaUrls(normalizeMediaUrls(lr.mediaUrls()));
                game.getLevels().add(level);

                // Индекс вопроса для каждой подсказки обычного уровня — связываем после создания вопросов.
                java.util.Map<LevelHint, Integer> hintQuestionIndexes = new java.util.HashMap<>();

                if (lr.hints() != null) {
                    for (int h = 0; h < lr.hints().size(); h++) {
                        GameRequest.HintRequest hr = lr.hints().get(h);
                        if (hr == null) continue;
                        if (hr.type() == null) {
                            throw new BadRequestException("Укажите тип подсказки");
                        }
                        LevelHint hint = new LevelHint();
                        hint.setLevel(level);
                        hint.setType(hr.type());
                        hint.setText(hr.text());
                        hint.setImageUrl(hr.imageUrl());
                        hint.setTriggerTime(hr.triggerTime());
                        hint.setPenaltyTime(hr.penaltyTime());
                        hint.setOrderIndex(h);
                        hint.setMediaUrls(normalizeMediaUrls(hr.mediaUrls()));
                        if (hr.questionIndex() != null) {
                            hintQuestionIndexes.put(hint, hr.questionIndex());
                        }
                        level.getHints().add(hint);
                    }
                }

                if (lr.type() == com.demo.taskmanager.entity.LevelType.ASSOCIATIONS && lr.associationColumns() != null) {
                    List<List<String>> columns = validateAndNormalizeColumns(lr.associationColumns());
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        List<String> words = columns.get(columnIndex);
                        for (int position = 0; position < words.size(); position++) {
                            LevelWord lw = new LevelWord();
                            lw.setLevel(level);
                            lw.setColumnNum(columnIndex + 1);
                            lw.setPosition(position + 1);
                            lw.setWord(words.get(position));
                            level.getWords().add(lw);
                        }
                    }
                }

                if (lr.type() == com.demo.taskmanager.entity.LevelType.NORMAL) {
                    List<GameRequest.StandardQuestionRequest> standardQuestions = new ArrayList<>();
                    List<Integer> questionIndexes = new ArrayList<>();
                    if (lr.standardQuestions() != null) {
                        for (int q = 0; q < lr.standardQuestions().size(); q++) {
                            GameRequest.StandardQuestionRequest sq = lr.standardQuestions().get(q);
                            if (sq == null) continue;
                            standardQuestions.add(sq);
                            questionIndexes.add(q);
                        }
                    } else if (lr.questions() != null) {
                        for (int q = 0; q < lr.questions().size(); q++) {
                            GameRequest.QuestionRequest qr = lr.questions().get(q);
                            if (qr == null) continue;
                            GameRequest.StandardQuestionRequest sq = new GameRequest.StandardQuestionRequest(qr.question(), null, null, qr.answers(), qr.mediaUrls(), qr.passThreshold());
                            standardQuestions.add(sq);
                            questionIndexes.add(q);
                        }
                    }
                    for (int q = 0; q < standardQuestions.size(); q++) {
                        GameRequest.StandardQuestionRequest sq = standardQuestions.get(q);
                        String questionText = sq.questionText() == null ? "" : sq.questionText().trim();
                        if (questionText.isEmpty()) {
                            throw new BadRequestException("Укажите вопрос");
                        }
                        List<String> answers = new ArrayList<>();
                        if (sq.answers() != null) {
                            for (String answer : sq.answers()) {
                                if (answer == null) continue;
                                String trimmed = answer.trim();
                                if (!trimmed.isEmpty()) {
                                    answers.add(trimmed);
                                }
                            }
                        }
                        if (answers.isEmpty()) {
                            throw new BadRequestException("Уровень " + (i + 1) + ", Вопрос " + (questionIndexes.get(q) + 1) + ": добавьте хотя бы один правильный ответ");
                        }
                        StandardQuestion standardQuestion = new StandardQuestion();
                        standardQuestion.setLevel(level);
                        standardQuestion.setQuestionText(questionText);
                        standardQuestion.setMediaType(sq.mediaType());
                        standardQuestion.setMediaUrl(sq.mediaUrl());
                        standardQuestion.setOrderIndex(q);
                        Integer passThreshold = sq.passThreshold();
                        if (passThreshold == null || passThreshold <= 0 || passThreshold > answers.size()) {
                            passThreshold = answers.size();
                        }
                        standardQuestion.setPassThreshold(passThreshold);
                        for (String answer : answers) {
                            StandardAnswer standardAnswer = new StandardAnswer();
                            standardAnswer.setQuestion(standardQuestion);
                            standardAnswer.setAnswerText(answer);
                            standardQuestion.getAnswers().add(standardAnswer);
                        }
                        level.getStandardQuestions().add(standardQuestion);
                    }
                    for (LevelHint hint : level.getHints()) {
                        Integer qi = hintQuestionIndexes.get(hint);
                        if (qi != null && qi >= 0 && qi < level.getStandardQuestions().size()) {
                            hint.setQuestion(level.getStandardQuestions().get(qi));
                        }
                    }
                }
            }
        }

        gameRepository.save(game);
        return GameResponse.from(game);
    }

    @Transactional
    public GameResponse updateGame(Long id, GameRequest request, User currentUser) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestException("Укажите название игры");
        }
        if (title.length() > 150) {
            throw new BadRequestException("Название игры не должно быть длиннее 150 символов");
        }

        Game game = findGame(id);
        requireCreator(game, currentUser);
        game.setTitle(title);
        game.setGameDate(request.gameDate());
        game.setDescription(request.description() == null ? null : request.description().trim());
        game.setVisibility(normalizeVisibility(request.visibility()));
        game.setMediaUrls(normalizeMediaUrls(request.mediaUrls()));
        Double timeLimit = request.timeLimitMinutes() == null ? (game.getTimeLimitMinutes() == null ? 10.0 : game.getTimeLimitMinutes()) : validateTimeLimit(request.timeLimitMinutes());
        game.setTimeLimitMinutes(timeLimit);
        game.setDifficulty(normalizeDifficulty(request.difficulty()));
        // ответы могли измениться — прогресс игроков по старой версии больше не актуален
        game.getProgressEntries().clear();

        game.clearWords();
        boolean hasAssociationLevel = request.levels() != null && request.levels().stream().anyMatch(l -> l != null && l.type() == com.demo.taskmanager.entity.LevelType.ASSOCIATIONS);
        if (hasAssociationLevel && request.columns() != null && !request.columns().isEmpty()) {
            List<List<String>> columns = validateAndNormalizeColumns(request.columns());
            fillWords(game, columns);
        } else if (hasAssociationLevel && (request.columns() == null || request.columns().isEmpty())) {
            throw new BadRequestException("Для уровня типа «Игра в ассоциации» необходимо заполнить пирамиду из 31 слова");
        }

        game.getLevels().clear();
        gameRepository.saveAndFlush(game);

        if (request.levels() != null && !request.levels().isEmpty()) {
            for (int i = 0; i < request.levels().size(); i++) {
                GameRequest.LevelRequest lr = request.levels().get(i);
                if (lr == null) {
                    throw new BadRequestException("Пустой уровень");
                }
                String levelTitle = lr.title() == null ? "" : lr.title().trim();
                if (levelTitle.isEmpty()) {
                    throw new BadRequestException("Укажите название уровня");
                }
                if (lr.type() == null) {
                    throw new BadRequestException("Укажите тип уровня");
                }
                GameLevel level = new GameLevel();
                level.setGame(game);
                level.setTitle(levelTitle);
                double levelTime = lr.timeLimitMinutes() == null ? timeLimit : validateTimeLimit(lr.timeLimitMinutes());
                level.setTimeLimitMinutes(levelTime);
                level.setType(lr.type());
                level.setHintsCount(lr.hintsCount() == null ? 0 : lr.hintsCount());
                level.setOrderIndex(i);
                level.setMediaUrls(normalizeMediaUrls(lr.mediaUrls()));
                game.getLevels().add(level);

                // Индекс вопроса для каждой подсказки обычного уровня — связываем после создания вопросов.
                java.util.Map<LevelHint, Integer> hintQuestionIndexes = new java.util.HashMap<>();

                if (lr.hints() != null) {
                    for (int h = 0; h < lr.hints().size(); h++) {
                        GameRequest.HintRequest hr = lr.hints().get(h);
                        if (hr == null) continue;
                        if (hr.type() == null) {
                            throw new BadRequestException("Укажите тип подсказки");
                        }
                        LevelHint hint = new LevelHint();
                        hint.setLevel(level);
                        hint.setType(hr.type());
                        hint.setText(hr.text());
                        hint.setImageUrl(hr.imageUrl());
                        hint.setTriggerTime(hr.triggerTime());
                        hint.setPenaltyTime(hr.penaltyTime());
                        hint.setOrderIndex(h);
                        hint.setMediaUrls(normalizeMediaUrls(hr.mediaUrls()));
                        if (hr.questionIndex() != null) {
                            hintQuestionIndexes.put(hint, hr.questionIndex());
                        }
                        level.getHints().add(hint);
                    }
                }

                if (lr.type() == com.demo.taskmanager.entity.LevelType.ASSOCIATIONS && lr.associationColumns() != null) {
                    List<List<String>> columns = validateAndNormalizeColumns(lr.associationColumns());
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        List<String> words = columns.get(columnIndex);
                        for (int position = 0; position < words.size(); position++) {
                            LevelWord lw = new LevelWord();
                            lw.setLevel(level);
                            lw.setColumnNum(columnIndex + 1);
                            lw.setPosition(position + 1);
                            lw.setWord(words.get(position));
                            level.getWords().add(lw);
                        }
                    }
                }

                if (lr.type() == com.demo.taskmanager.entity.LevelType.NORMAL) {
                    List<GameRequest.StandardQuestionRequest> standardQuestions = new ArrayList<>();
                    List<Integer> questionIndexes = new ArrayList<>();
                    if (lr.standardQuestions() != null) {
                        for (int q = 0; q < lr.standardQuestions().size(); q++) {
                            GameRequest.StandardQuestionRequest sq = lr.standardQuestions().get(q);
                            if (sq == null) continue;
                            standardQuestions.add(sq);
                            questionIndexes.add(q);
                        }
                    } else if (lr.questions() != null) {
                        for (int q = 0; q < lr.questions().size(); q++) {
                            GameRequest.QuestionRequest qr = lr.questions().get(q);
                            if (qr == null) continue;
                            GameRequest.StandardQuestionRequest sq = new GameRequest.StandardQuestionRequest(qr.question(), null, null, qr.answers(), qr.mediaUrls(), qr.passThreshold());
                            standardQuestions.add(sq);
                            questionIndexes.add(q);
                        }
                    }
                    for (int q = 0; q < standardQuestions.size(); q++) {
                        GameRequest.StandardQuestionRequest sq = standardQuestions.get(q);
                        String questionText = sq.questionText() == null ? "" : sq.questionText().trim();
                        if (questionText.isEmpty()) {
                            throw new BadRequestException("Укажите вопрос");
                        }
                        List<String> answers = new ArrayList<>();
                        if (sq.answers() != null) {
                            for (String answer : sq.answers()) {
                                if (answer == null) continue;
                                String trimmed = answer.trim();
                                if (!trimmed.isEmpty()) {
                                    answers.add(trimmed);
                                }
                            }
                        }
                        if (answers.isEmpty()) {
                            throw new BadRequestException("Уровень " + (i + 1) + ", Вопрос " + (questionIndexes.get(q) + 1) + ": добавьте хотя бы один правильный ответ");
                        }
                        StandardQuestion standardQuestion = new StandardQuestion();
                        standardQuestion.setLevel(level);
                        standardQuestion.setQuestionText(questionText);
                        standardQuestion.setMediaType(sq.mediaType());
                        standardQuestion.setMediaUrl(sq.mediaUrl());
                        standardQuestion.setOrderIndex(q);
                        Integer passThreshold = sq.passThreshold();
                        if (passThreshold == null || passThreshold <= 0 || passThreshold > answers.size()) {
                            passThreshold = answers.size();
                        }
                        standardQuestion.setPassThreshold(passThreshold);
                        for (String answer : answers) {
                            StandardAnswer standardAnswer = new StandardAnswer();
                            standardAnswer.setQuestion(standardQuestion);
                            standardAnswer.setAnswerText(answer);
                            standardQuestion.getAnswers().add(standardAnswer);
                        }
                        level.getStandardQuestions().add(standardQuestion);
                    }
                    for (LevelHint hint : level.getHints()) {
                        Integer qi = hintQuestionIndexes.get(hint);
                        if (qi != null && qi >= 0 && qi < level.getStandardQuestions().size()) {
                            hint.setQuestion(level.getStandardQuestions().get(qi));
                        }
                    }
                }
            }
        }

        gameRepository.save(game);
        return GameResponse.from(game);
    }

    /** Пометить игру активной: именно она открывается в режиме игрока по умолчанию. */
    @Transactional
    public GameSummary activateGame(Long id, User currentUser) {
        Game game = findGame(id);
        requireCreator(game, currentUser);
        gameRepository.findAll().forEach(other -> other.setActive(other.getId().equals(id)));
        return GameSummary.from(game);
    }

    @Transactional
    public void deleteGame(Long id, User currentUser) {
        Game game = findGame(id);
        requireCreator(game, currentUser);
        boolean wasActive = game.isActive();
        gameRepository.delete(game);
        // если удалили активную игру — активной становится самая свежая из оставшихся
        if (wasActive) {
            gameRepository.findAllByOrderByCreatedAtDesc().stream()
                    .findFirst()
                    .ifPresent(next -> next.setActive(true));
        }
    }

    /**
     * Сохранить время прохождения. Если на сервере уже зафиксировано время победы
     * этого пользователя, берём его — подделать результат из клиента нельзя.
     * После истечения лимита результат не принимается.
     */
    @Transactional
    public ResultResponse submitResult(Long gameId, User user, Integer timeSeconds) {
        Game game = findGame(gameId);
        UserProgress progress = progressRepository
                .findByGameIdAndUserId(gameId, user.getId())
                .orElseThrow(() -> new BadRequestException("Сначала начните игру"));
        int seconds = validateTime(timeSeconds);

        GameResult result = new GameResult(game, user, seconds);
        resultRepository.save(result);
        // Повторная отправка результата разрешена (в рейтинг идёт лучшая попытка),
        // но победа текущей попытки начисляет командам +1 только один раз.
        if (!progress.isWinCounted()) {
            progress.setWinCounted(true);
            progress.setUpdatedAt(Instant.now());
            progressRepository.save(progress);
            teamService.registerWin(user);
        }
        return ResultResponse.from(result);
    }

    /**
     * Раскрыть правильный ответ колонки 5 после истечения времени.
     * До таймаута ответ не отдаётся, после победы также не нужен.
     */
    @Transactional(readOnly = true)
    public TimeoutResponse revealTimeoutAnswer(Long gameId, User user) {
        Game game = findGame(gameId);
        UserProgress progress = progressRepository.findByGameIdAndUserId(gameId, user.getId())
                .orElseThrow(() -> new BadRequestException("Игра ещё не начата"));
        if (progress.getFinishedSeconds() != null) {
            throw new BadRequestException("Игра уже пройдена");
        }
        // допускаем секундную рассинхронизацию между клиентом и сервером
        if (elapsedSeconds(progress) + 1 < limitSeconds(game)) {
            throw new BadRequestException("Время ещё не вышло");
        }
        return new TimeoutResponse(wordAt(game, COLUMN_SIZES.length, 1));
    }

    /**
     * Рейтинг игры: по одной (лучшей) попытке на пользователя,
     * сортировка по возрастанию времени.
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(Long gameId) {
        findGame(gameId);
        List<GameResult> results = resultRepository
                .findByGameIdOrderByTimeSecondsAscCompletedAtAsc(gameId);

        Map<Long, GameResult> bestByUser = new LinkedHashMap<>();
        for (GameResult result : results) {
            bestByUser.putIfAbsent(result.getUser().getId(), result);
        }

        List<LeaderboardEntry> entries = new ArrayList<>(bestByUser.size());
        int place = 1;
        for (GameResult best : bestByUser.values()) {
            entries.add(new LeaderboardEntry(place++, best.getUser().getId(), best.getUser().getUsername(),
                    best.getTimeSeconds(), best.getCompletedAt()));
        }
        return entries;
    }

    private Game findGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Игра не найдена"));
    }

    /** Текущий уровень игры: первый по порядку. */
    private GameLevel currentLevel(Game game) {
        return game.getLevels() == null ? null : game.getLevels().stream()
                .filter(level -> level != null)
                .sorted(Comparator.comparingInt(GameLevel::getOrderIndex))
                .findFirst()
                .orElse(null);
    }

    /** Редактировать и удалять игру может только её создатель. */
    private void requireCreator(Game game, User user) {
        if (user == null || game.getCreator() == null
                || !game.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Изменять игру может только её создатель");
        }
    }

    private UserProgress findOrCreateProgress(Game game, User user) {
        UserProgress existing = progressRepository.findByGameIdAndUserId(game.getId(), user.getId())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        // идемпотентно: параллельные открытия игры не создают дубль
        progressRepository.insertIfAbsent(user.getId(), game.getId());
        return progressRepository.findByGameIdAndUserId(game.getId(), user.getId())
                .orElseThrow(() -> new IllegalStateException("Не удалось создать прогресс игры"));
    }

    private String wordAt(Game game, int column, int position) {
        return game.getWords().stream()
                .filter(word -> word.getColumnNum() == column && word.getPosition() == position)
                .map(GameWord::getWord)
                .findFirst()
                .orElse(null);
    }

    private GameProgressDto toDto(UserProgress progress) {
        long elapsed = progress.getFinishedSeconds() != null
                ? progress.getFinishedSeconds()
                : elapsedSeconds(progress);
        return new GameProgressDto(parseSolved(progress), progress.getStartedAt(),
                elapsed, progress.getFinishedSeconds(),
                progress.getHintLevel(), parsePrefixes(progress));
    }

    private long elapsedSeconds(UserProgress progress) {
        if (progress.getStartedAt() == null) return 0;
        return Math.max(0, Duration.between(progress.getStartedAt(), Instant.now()).getSeconds());
    }

    private Map<String, String> parseSolved(UserProgress progress) {
        try {
            Map<String, String> solved = objectMapper.readValue(progress.getSolvedWords(),
                    new TypeReference<LinkedHashMap<String, String>>() { });
            return solved == null ? new LinkedHashMap<>() : solved;
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeSolved(UserProgress progress, Map<String, String> solved) {
        try {
            progress.setSolvedWords(objectMapper.writeValueAsString(solved));
        } catch (JsonProcessingException e) {
            progress.setSolvedWords("{}");
        }
    }

    private Map<String, String> parsePrefixes(UserProgress progress) {
        try {
            Map<String, String> prefixes = objectMapper.readValue(
                    progress.getHintPrefixes() == null ? "{}" : progress.getHintPrefixes(),
                    new TypeReference<LinkedHashMap<String, String>>() { });
            return prefixes == null ? new LinkedHashMap<>() : prefixes;
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private void writePrefixes(UserProgress progress, Map<String, String> prefixes) {
        try {
            progress.setHintPrefixes(objectMapper.writeValueAsString(prefixes));
        } catch (JsonProcessingException e) {
            progress.setHintPrefixes("{}");
        }
    }

    private int validateTime(Integer timeSeconds) {
        if (timeSeconds == null || timeSeconds < 1 || timeSeconds > MAX_TIME_SECONDS) {
            throw new BadRequestException("Время прохождения должно быть от 1 до "
                    + MAX_TIME_SECONDS + " секунд");
        }
        return timeSeconds;
    }

    /** Лимит времени игры обязателен: положительное число минут, можно дробное. */
    private double validateTimeLimit(Double timeLimitMinutes) {
        if (timeLimitMinutes == null || !Double.isFinite(timeLimitMinutes)
                || timeLimitMinutes <= 0) {
            throw new BadRequestException("Укажите время на прохождение в минутах (больше 0)");
        }
        if (timeLimitMinutes > MAX_TIME_LIMIT_MINUTES) {
            throw new BadRequestException("Время на прохождение не должно превышать "
                    + (int) MAX_TIME_LIMIT_MINUTES + " минут");
        }
        return timeLimitMinutes;
    }

    private int limitSeconds(Game game) {
        return (int) Math.max(1, Math.round(game.getTimeLimitMinutes() * 60));
    }

    private boolean isTimeUp(Game game, UserProgress progress) {
        return elapsedSeconds(progress) >= limitSeconds(game);
    }

    /** Сравнение слов: без учёта регистра и пробелов по краям. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Нормализация сложности игры: от 1 до 5 (звёзды). */
    private int normalizeDifficulty(Integer difficulty) {
        if (difficulty == null) return 3;
        return Math.max(1, Math.min(5, difficulty));
    }

    /** Нормализация видимости: PUBLIC — для всех, TEAM — для моей команды. */
    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.trim().isEmpty()) return "PUBLIC";
        String value = visibility.trim().toUpperCase(Locale.ROOT);
        return value.equals("TEAM") ? "TEAM" : "PUBLIC";
    }

    /** Нормализация списка медиафайлов: максимум 10 непустых URL. */
    private List<String> normalizeMediaUrls(List<String> mediaUrls) {
        List<String> result = new ArrayList<>();
        if (mediaUrls == null) return result;
        for (String url : mediaUrls) {
            if (url == null) continue;
            String trimmed = url.trim();
            if (!trimmed.isEmpty() && result.size() < 10) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Раскладывает колонки по строкам таблицы game_words. */
    private void fillWords(Game game, List<List<String>> columns) {
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            List<String> words = columns.get(columnIndex);
            for (int position = 0; position < words.size(); position++) {
                game.addWord(columnIndex + 1, position + 1, words.get(position));
            }
        }
    }

    /**
     * Проверка колонок и нормализация слов (обрезка пробелов).
     */
    private List<List<String>> validateAndNormalizeColumns(List<List<String>> columns) {
        if (columns == null || columns.size() != COLUMN_SIZES.length) {
            throw new BadRequestException("Игра должна состоять из 5 колонок (16 + 8 + 4 + 2 + 1 слово)");
        }

        List<List<String>> normalized = new ArrayList<>(COLUMN_SIZES.length);
        List<String> emptyCells = new ArrayList<>();

        for (int columnIndex = 0; columnIndex < COLUMN_SIZES.length; columnIndex++) {
            int expected = COLUMN_SIZES[columnIndex];
            List<String> column = columns.get(columnIndex);
            if (column == null || column.size() != expected) {
                throw new BadRequestException("В колонке " + (columnIndex + 1) + " должно быть "
                        + expected + " слов (сейчас " + (column == null ? 0 : column.size()) + ")");
            }

            List<String> words = new ArrayList<>(expected);
            for (int position = 0; position < expected; position++) {
                String word = column.get(position) == null ? "" : column.get(position).trim();
                if (word.isEmpty()) {
                    emptyCells.add("колонка " + (columnIndex + 1) + ", слово " + (position + 1));
                } else if (word.length() > MAX_WORD_LENGTH) {
                    throw new BadRequestException("Слово в колонке " + (columnIndex + 1)
                            + " длиннее " + MAX_WORD_LENGTH + " символов");
                }
                words.add(word);
            }
            normalized.add(words);
        }

        if (!emptyCells.isEmpty()) {
            throw new BadRequestException("Заполните все " + TOTAL_WORDS + " слово. Пустые: "
                    + String.join(", ", emptyCells.subList(0, Math.min(emptyCells.size(), 5)))
                    + (emptyCells.size() > 5 ? " и ещё " + (emptyCells.size() - 5) : ""));
        }
        return normalized;
    }

    /**
     * Проверка игры и нормализация слов (обрезка пробелов).
     * Требования: название непустое, в колонках ровно 16/8/4/2/1 непустых слов.
     */
    private List<List<String>> validateAndNormalize(GameRequest request) {
        if (request == null) {
            throw new BadRequestException("Пустой запрос");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw new BadRequestException("Укажите название игры");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("Название игры не должно быть длиннее "
                    + MAX_TITLE_LENGTH + " символов");
        }
        List<List<String>> columns = request.columns();
        if (columns == null || columns.isEmpty()) {
            throw new BadRequestException("Игра должна состоять из 5 колонок (16 + 8 + 4 + 2 + 1 слово)");
        }
        return validateAndNormalizeColumns(columns);
    }
}
