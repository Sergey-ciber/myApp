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
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameResult;
import com.demo.taskmanager.entity.GameWord;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.entity.UserProgress;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.GameResultRepository;
import com.demo.taskmanager.repository.UserProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    /** Подсказки открываются по серверному таймеру: 3, 6, 9, 12 минут после старта. */
    private static final int HINT_LEVEL_ONE_SECONDS = 180;
    private static final int HINT_LEVEL_TWO_SECONDS = 360;
    private static final int HINT_LEVEL_THREE_SECONDS = 540;
    private static final int HINT_LEVEL_FOUR_SECONDS = 720;
    private static final int MAX_HINT_LEVEL = 4;

    private final GameRepository gameRepository;
    private final GameResultRepository resultRepository;
    private final UserProgressRepository progressRepository;
    private final ObjectMapper objectMapper;

    public GameService(GameRepository gameRepository,
                       GameResultRepository resultRepository,
                       UserProgressRepository progressRepository,
                       ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.resultRepository = resultRepository;
        this.progressRepository = progressRepository;
        this.objectMapper = objectMapper;
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
        if (request == null || request.column() == null || request.position() == null) {
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
            // верное слово колонки 5 — победа, время считает сервер
            if (column == COLUMN_SIZES.length && progress.getFinishedSeconds() == null) {
                progress.setFinishedSeconds((int) Math.max(1, elapsedSeconds(progress)));
            }
            progress.setUpdatedAt(Instant.now());
            progressRepository.save(progress);
        }
        return new CheckAnswerResponse(correct, toDto(progress));
    }

    /**
     * Выдать следующую подсказку. Доступность считается только по серверному времени:
     * первая — через 180 секунд после старта, вторая — через 360, третья через 520.
     * Подсказка открывает первые буквы всех ещё не разгаданных ячеек.
     */
    @Transactional
    public GameProgressDto takeHint(Long gameId, User user) {
        Game game = findGame(gameId);
        UserProgress progress = findOrCreateProgress(game, user);
        if (progress.getFinishedSeconds() != null) {
            throw new BadRequestException("Игра уже пройдена — подсказка не нужна");
        }
        int level = progress.getHintLevel();
        if (level >= MAX_HINT_LEVEL) {
            throw new BadRequestException("Все подсказки уже получены");
        }


        int required = switch (level) {
            case 0 -> HINT_LEVEL_ONE_SECONDS;
            case 1 -> HINT_LEVEL_TWO_SECONDS;
            case 2 -> HINT_LEVEL_THREE_SECONDS;
            case 3 -> HINT_LEVEL_FOUR_SECONDS;
            default -> Integer.MAX_VALUE;
        };
        long elapsed = elapsedSeconds(progress);
        if (elapsed < required) {
            throw new BadRequestException("Подсказка будет доступна через "
                    + (required - elapsed) + " с");
        }

        int newLevel = level + 1;
        Map<String, String> solved = parseSolved(progress);
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (GameWord word : game.getWords()) {
            if (word.getColumnNum() < 2) {
                continue;
            }
            String key = word.getColumnNum() + ":" + word.getPosition();
            if (solved.containsKey(key)) {
                continue;
            }
            String value = word.getWord();
            if (value == null || value.isEmpty()) {
                continue;
            }
            prefixes.put(key, value.substring(0, Math.min(newLevel, value.length())));
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
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);
        return toDto(progress);
    }

    @Transactional
    public GameResponse createGame(GameRequest request, User creator) {
        List<List<String>> columns = validateAndNormalize(request);

        Game game = new Game();
        game.setTitle(request.title().trim());
        game.setCreator(creator);
        // первая игра сразу становится активной — её и откроет игрок
        game.setActive(gameRepository.count() == 0);
        fillWords(game, columns);
        gameRepository.save(game);
        return GameResponse.from(game);
    }

    @Transactional
    public GameResponse updateGame(Long id, GameRequest request, User currentUser) {
        List<List<String>> columns = validateAndNormalize(request);

        Game game = findGame(id);
        requireCreator(game, currentUser);
        game.setTitle(request.title().trim());
        // ответы могли измениться — прогресс игроков по старой версии больше не актуален
        game.getProgressEntries().clear();
        // сначала удаляем старые слова и фиксируем удаление,
        // иначе вставка новых слов упрётся в уникальный индекс (game_id, column_num, position)
        game.clearWords();
        gameRepository.saveAndFlush(game);
        fillWords(game, columns);
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
     */
    @Transactional
    public ResultResponse submitResult(Long gameId, User user, Integer timeSeconds) {
        Game game = findGame(gameId);
        UserProgress progress = progressRepository
                .findByGameIdAndUserId(gameId, user.getId()).orElse(null);
        Integer serverTime = progress == null ? null : progress.getFinishedSeconds();
        int seconds = serverTime != null ? serverTime : validateTime(timeSeconds);

        GameResult result = new GameResult(game, user, seconds);
        resultRepository.save(result);
        return ResultResponse.from(result);
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

    /** Сравнение слов: без учёта регистра и пробелов по краям. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
}
