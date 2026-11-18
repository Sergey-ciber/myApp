package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.LevelStatsDto;
import com.demo.taskmanager.dto.ResultRequest;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.entity.FinishReason;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.GameResult;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.entity.UserProgress;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.GameResultRepository;
import com.demo.taskmanager.repository.StandardQuestionRepository;
import com.demo.taskmanager.repository.TeamGameSessionRepository;
import com.demo.taskmanager.repository.TeamHintStateRepository;
import com.demo.taskmanager.repository.TeamLevelStatRepository;
import com.demo.taskmanager.repository.TeamMemberRepository;
import com.demo.taskmanager.repository.TeamQuestionStateRepository;
import com.demo.taskmanager.repository.TeamSessionEventRepository;
import com.demo.taskmanager.repository.TeamSessionPresenceRepository;
import com.demo.taskmanager.repository.TeamSessionProgressRepository;
import com.demo.taskmanager.repository.UserGameProgressRepository;
import com.demo.taskmanager.repository.UserProgressRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Завершение одиночной попытки: успешное прохождение, таймаут игры,
 * таймаут уровня и досрочный отказ игрока.
 *
 * Проверяем главное бизнес-правило:
 *  - completed и таймауты создают результат и попадают в рейтинг;
 *  - отказ игрока не создаёт результат и не меняет рейтинг;
 *  - победа команде начисляется только за полностью пройденную игру.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceFinishReasonTest {

    private static final Long GAME_ID = 121L;
    private static final Long USER_ID = 7L;

    @Mock private GameRepository gameRepository;
    @Mock private GameResultRepository resultRepository;
    @Mock private UserProgressRepository progressRepository;
    @Mock private TeamService teamService;
    @Mock private StandardQuestionRepository standardQuestionRepository;
    @Mock private TeamSessionProgressRepository teamSessionProgressRepository;
    @Mock private TeamQuestionStateRepository teamQuestionStateRepository;
    @Mock private TeamHintStateRepository teamHintStateRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserGameProgressRepository userGameProgressRepository;
    @Mock private TeamGameSessionRepository teamGameSessionRepository;
    @Mock private TeamSessionPresenceRepository teamSessionPresenceRepository;
    @Mock private TeamSessionEventRepository teamSessionEventRepository;
    @Mock private TeamLevelStatRepository teamLevelStatRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<GameResult> savedResults = new ArrayList<>();

    private GameService service;
    private Game game;
    private User user;
    private GameLevel firstLevel;
    private GameLevel secondLevel;
    private UserProgress progress;

    @BeforeEach
    void setUp() {
        service = new GameService(
                gameRepository,
                resultRepository,
                progressRepository,
                teamService,
                objectMapper,
                standardQuestionRepository,
                teamSessionProgressRepository,
                teamQuestionStateRepository,
                teamHintStateRepository,
                teamMemberRepository,
                userGameProgressRepository,
                teamGameSessionRepository,
                teamSessionPresenceRepository,
                teamSessionEventRepository,
                teamLevelStatRepository
        );

        user = new User();
        user.setId(USER_ID);
        user.setUsername("tester");

        game = new Game();
        game.setId(GAME_ID);
        game.setTitle("Виноградный день");
        game.setTimeLimitMinutes(60.0);

        firstLevel = level(128L, "Уровень 1", 0);
        secondLevel = level(131L, "Уровень 2", 1);
        game.setLevels(new ArrayList<>(List.of(firstLevel, secondLevel)));

        progress = new UserProgress(game, user);
        progress.setStartedAt(Instant.now().minusSeconds(30));
        progress.setLevelStartedAt(Instant.now().minusSeconds(20));
        progress.setLevelIndex(1);

        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(progressRepository.findByGameIdAndUserIdForUpdate(GAME_ID, USER_ID))
                .thenReturn(Optional.of(progress));
        when(resultRepository.save(any(GameResult.class))).thenAnswer(invocation -> {
            GameResult result = invocation.getArgument(0);
            savedResults.add(result);
            return result;
        });
        when(resultRepository.findByGameId(GAME_ID))
                .thenAnswer(invocation -> List.copyOf(savedResults));
        when(resultRepository.findByGameIdOrderByTimeSecondsAscCompletedAtAsc(GAME_ID))
                .thenAnswer(invocation -> List.copyOf(savedResults));
        when(teamGameSessionRepository
                .findByGameIdAndStatusInOrderByCreatedAtDesc(anyLong(), anyList()))
                .thenReturn(List.of());
    }

    @Test
    void completedCreatesResultAndCountsAsWin() {
        ResultResponse response = service.submitResult(GAME_ID, user,
                new ResultRequest(null, List.of(
                        stat(firstLevel, 30, 5, false),
                        stat(secondLevel, 40, 0, false)
                ), "COMPLETED"));

        assertThat(response.finishReason()).isEqualTo("COMPLETED");
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.COMPLETED);
        assertThat(savedResults.get(0).getTimeSeconds()).isEqualTo(75);
        assertThat(progress.isWinCounted()).isTrue();
        assertThat(progress.getFinishReason()).isNull();
        verify(teamService).registerWin(user);
        verify(userGameProgressRepository).deleteByUserIdAndGameId(USER_ID, GAME_ID);

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);
        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).timeSeconds()).isEqualTo(75);
        assertThat(leaderboard.get(0).userId()).isEqualTo(USER_ID);
    }

    @Test
    void timeExpiredCreatesResultButDoesNotCountAsWin() {
        // Общий лимит игры — 60 минут, но попытка началась 2 часа назад.
        game.setTimeLimitMinutes(60.0);
        progress.setStartedAt(Instant.now().minusSeconds(2 * 60 * 60));
        progress.setLevelIndex(0);

        ResultResponse response = service.submitResult(GAME_ID, user,
                new ResultRequest(null, List.of(
                        stat(firstLevel, 999, 10, true)
                ), "TIME_EXPIRED"));

        assertThat(response.finishReason()).isEqualTo("TIME_EXPIRED");
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        // Время истёкшего уровня сервер считает по лимиту уровня (10 минут = 600 с),
        // а не по клиентским 999 секундам.
        assertThat(savedResults.get(0).getTimeSeconds()).isEqualTo(610);
        assertThat(progress.getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        assertThat(progress.getFinishedSeconds()).isEqualTo(610);
        assertThat(progress.isWinCounted()).isFalse();
        verify(teamService, never()).registerWin(any(User.class));

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);
        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).timeSeconds()).isEqualTo(610);
    }

    @Test
    void levelTimeExpiredCreatesResultButDoesNotCountAsWin() {
        // Общий лимит ещё не вышел: попытка началась 2 минуты назад.
        game.setTimeLimitMinutes(60.0);
        progress.setStartedAt(Instant.now().minusSeconds(120));
        // А лимит текущего уровня (10 минут) уже истёк.
        progress.setLevelStartedAt(Instant.now().minusSeconds(11 * 60));
        progress.setLevelIndex(1);

        ResultResponse response = service.submitResult(GAME_ID, user,
                new ResultRequest(null, List.of(
                        stat(firstLevel, 100, 0, false),
                        stat(secondLevel, 10, 0, true)
                ), "LEVEL_TIME_EXPIRED"));

        assertThat(response.finishReason()).isEqualTo("LEVEL_TIME_EXPIRED");
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason())
                .isEqualTo(FinishReason.LEVEL_TIME_EXPIRED);
        // 100 секунд первого уровня + полный лимит второго (600 секунд).
        assertThat(savedResults.get(0).getTimeSeconds()).isEqualTo(700);
        assertThat(progress.getFinishReason()).isEqualTo(FinishReason.LEVEL_TIME_EXPIRED);
        assertThat(progress.isWinCounted()).isFalse();
        verify(teamService, never()).registerWin(any(User.class));

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);
        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).timeSeconds()).isEqualTo(700);
    }

    @Test
    void completedAfterOverallLimitCreatesTimeoutResult() {
        // Общий лимит игры — 60 минут, но попытка началась 2 часа назад.
        game.setTimeLimitMinutes(60.0);
        progress.setStartedAt(Instant.now().minusSeconds(2 * 60 * 60));
        progress.setLevelIndex(1);

        ResultResponse response = service.submitResult(GAME_ID, user,
                new ResultRequest(null, List.of(
                        stat(firstLevel, 30, 5, false),
                        stat(secondLevel, 40, 0, false)
                ), "COMPLETED"));

        // Дорешать игру после общего дедлайна нельзя: это таймаут, а не победа.
        assertThat(response.finishReason()).isEqualTo("TIME_EXPIRED");
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        assertThat(progress.getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        assertThat(progress.isWinCounted()).isFalse();
        verify(teamService, never()).registerWin(any(User.class));
    }

    @Test
    void serverTimerClosesExpiredAttemptWithoutClient() {
        progress.setStartedAt(Instant.now().minusSeconds(2 * 60 * 60));
        progress.setLevelStartedAt(Instant.now().minusSeconds(2 * 60 * 60));
        progress.setLevelIndex(1);
        when(progressRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(progress));

        service.tickSoloAttempt(1L);

        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        assertThat(savedResults.get(0).getTimeSeconds()).isEqualTo(600);
        assertThat(progress.getFinishReason()).isEqualTo(FinishReason.TIME_EXPIRED);
        assertThat(progress.getFinishedSeconds()).isEqualTo(600);
        assertThat(progress.isWinCounted()).isFalse();
        verify(teamService, never()).registerWin(any(User.class));
    }

    @Test
    void serverTimerClosesLevelOnlyAfterGracePeriod() {
        // Общий лимит (60 минут) не истёк, а лимит уровня (10 минут) — истёк.
        progress.setStartedAt(Instant.now().minusSeconds(20 * 60));
        progress.setLevelStartedAt(Instant.now().minusSeconds(10 * 60 + 2));
        progress.setLevelIndex(1);
        when(progressRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(progress));

        // В пределах запаса таймер ждёт: клиент ещё может прислать результат.
        service.tickSoloAttempt(1L);
        assertThat(savedResults).isEmpty();
        assertThat(progress.getFinishReason()).isNull();

        progress.setLevelStartedAt(Instant.now().minusSeconds(10 * 60 + 10));
        service.tickSoloAttempt(1L);

        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.LEVEL_TIME_EXPIRED);
        assertThat(progress.getFinishReason()).isEqualTo(FinishReason.LEVEL_TIME_EXPIRED);
        assertThat(progress.getFinishedSeconds()).isEqualTo(600);
    }

    @Test
    void userAbortedDeletesProgressAndCreatesNoResult() {
        service.abortSoloGame(GAME_ID, user);

        verify(progressRepository).delete(progress);
        verify(userGameProgressRepository).deleteByUserIdAndGameId(USER_ID, GAME_ID);
        verify(resultRepository, never()).save(any(GameResult.class));
        verify(teamService, never()).registerWin(any(User.class));

        // Рейтинг пуст: результат прерванной попытки нигде не сохранился.
        when(progressRepository.findByGameIdAndUserId(GAME_ID, USER_ID))
                .thenReturn(Optional.empty());
        assertThat(service.leaderboard(GAME_ID)).isEmpty();
    }

    private GameLevel level(Long id, String title, int orderIndex) {
        GameLevel level = new GameLevel();
        level.setId(id);
        level.setGame(game);
        level.setTitle(title);
        level.setOrderIndex(orderIndex);
        level.setTimeLimitMinutes(10.0);
        return level;
    }

    private LevelStatsDto stat(GameLevel level, int netSeconds, int penaltySeconds,
                               boolean timedOut) {
        return LevelStatsDto.of(level.getId(), level.getTitle(), netSeconds, penaltySeconds,
                timedOut);
    }
}
