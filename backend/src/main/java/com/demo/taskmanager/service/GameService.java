package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.GameSummary;
import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameResult;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.GameResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final GameRepository gameRepository;
    private final GameResultRepository resultRepository;

    public GameService(GameRepository gameRepository, GameResultRepository resultRepository) {
        this.gameRepository = gameRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public List<GameSummary> listGames() {
        return gameRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(GameSummary::from)
                .toList();
    }

    /** Игру может открыть любой авторизованный пользователь. */
    @Transactional(readOnly = true)
    public GameResponse getGame(Long id) {
        return GameResponse.from(findGame(id));
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

    /** Сохранить время прохождения. Играть может любой пользователь, включая автора. */
    @Transactional
    public ResultResponse submitResult(Long gameId, User user, Integer timeSeconds) {
        Game game = findGame(gameId);
        if (timeSeconds == null || timeSeconds < 1 || timeSeconds > MAX_TIME_SECONDS) {
            throw new BadRequestException("Время прохождения должно быть от 1 до "
                    + MAX_TIME_SECONDS + " секунд");
        }
        GameResult result = new GameResult(game, user, timeSeconds);
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
