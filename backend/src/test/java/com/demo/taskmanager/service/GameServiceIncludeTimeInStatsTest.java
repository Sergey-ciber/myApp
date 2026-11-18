package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.GameRequest;
import com.demo.taskmanager.dto.GameResponse;
import com.demo.taskmanager.dto.LeaderboardEntry;
import com.demo.taskmanager.dto.LevelStatsDto;
import com.demo.taskmanager.dto.ResultRequest;
import com.demo.taskmanager.dto.ResultResponse;
import com.demo.taskmanager.entity.FinishReason;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.GameResult;
import com.demo.taskmanager.entity.LevelType;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;
import com.demo.taskmanager.entity.TeamLevelStat;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Флаг учёта времени уровня в общей статистике/рейтинге.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceIncludeTimeInStatsTest {

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
    private GameLevel includedLevel;
    private GameLevel excludedLevel;
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
        game.setTitle("Тестовая игра");
        game.setTimeLimitMinutes(60.0);

        includedLevel = level(128L, "Уровень 1 (NORMAL)", 0, LevelType.NORMAL, true);
        excludedLevel = level(131L, "Уровень 2 (ASSOCIATIONS)", 1, LevelType.ASSOCIATIONS, false);
        game.setLevels(new ArrayList<>(List.of(includedLevel, excludedLevel)));

        progress = new UserProgress(game, user);
        progress.setStartedAt(Instant.now().minusSeconds(300));
        progress.setLevelIndex(1);
        progress.setLevelStartedAt(Instant.now().minusSeconds(120));

        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(progressRepository.findByGameIdAndUserIdForUpdate(GAME_ID, USER_ID)).thenReturn(Optional.of(progress));
        when(resultRepository.save(any(GameResult.class))).thenAnswer(inv -> {
            GameResult r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", (long) (savedResults.size() + 1));
            }
            savedResults.add(r);
            return r;
        });
        when(teamGameSessionRepository.findByGameIdAndStatusInOrderByCreatedAtDesc(eq(GAME_ID), anyList()))
                .thenReturn(List.of());
        savedResults.clear();
    }

    @Test
    void completedSoloTotalsExcludeFlaggedLevelButKeepPerLevelTimes() throws Exception {
        ResultRequest request = new ResultRequest(null, List.of(
                LevelStatsDto.of(includedLevel.getId(), includedLevel.getTitle(), 100, 10, false, true),
                LevelStatsDto.of(excludedLevel.getId(), excludedLevel.getTitle(), 500, 20, false, false)
        ), "COMPLETED");

        ResultResponse response = service.submitResult(GAME_ID, user, request);

        assertThat(response.timeSeconds()).isEqualTo(110);
        assertThat(savedResults).hasSize(1);
        GameResult saved = savedResults.get(0);
        assertThat(saved.getTimeSeconds()).isEqualTo(110);
        List<LevelStatsDto> levels = objectMapper.readValue(saved.getLevelsStats(), new TypeReference<>() {});
        assertThat(levels).hasSize(2);
        assertThat(levels.get(0).countsInTotals()).isTrue();
        assertThat(levels.get(0).totalSeconds()).isEqualTo(110);
        assertThat(levels.get(1).countsInTotals()).isFalse();
        assertThat(levels.get(1).totalSeconds()).isEqualTo(520);
    }

    @Test
    void timeoutSoloTotalsExcludeFlaggedLevel() throws Exception {
        // Истёк таймер текущего (исключённого из итогов) уровня, общий таймер не истёк.
        excludedLevel.setTimeLimitMinutes(1.0);
        progress.setLevelIndex(1);
        progress.setLevelStartedAt(Instant.now().minusSeconds(120));
        progress.setStartedAt(Instant.now().minusSeconds(300));

        ResultRequest request = new ResultRequest(null, List.of(
                LevelStatsDto.of(includedLevel.getId(), includedLevel.getTitle(), 100, 10, false, true),
                LevelStatsDto.of(excludedLevel.getId(), excludedLevel.getTitle(), 50, 20, false, false)
        ), "LEVEL_TIME_EXPIRED");

        ResultResponse response = service.submitResult(GAME_ID, user, request);

        assertThat(response.timeSeconds()).isEqualTo(110);
        assertThat(savedResults).hasSize(1);
        assertThat(savedResults.get(0).getFinishReason()).isEqualTo(FinishReason.LEVEL_TIME_EXPIRED);
        List<LevelStatsDto> levels = objectMapper.readValue(savedResults.get(0).getLevelsStats(), new TypeReference<>() {});
        assertThat(levels).hasSize(2);
        assertThat(levels.get(0).countsInTotals()).isTrue();
        assertThat(levels.get(0).totalSeconds()).isEqualTo(110);
        assertThat(levels.get(1).countsInTotals()).isFalse();
        assertThat(levels.get(1).timedOut()).isTrue();
        assertThat(levels.get(1).totalSeconds()).isEqualTo(80); // лимит уровня (60) + штраф (20), но в итог не входит
    }

    @Test
    void leaderboardRecalculatesEffectiveTimeFromCurrentLevelFlag() throws Exception {
        // Старый результат сохранён, когда оба уровня учитывались (110 + 520 = 630),
        // но сейчас второй уровень исключён из итогов — в рейтинге должно быть 110.
        GameResult result = new GameResult(game, user, 630);
        ReflectionTestUtils.setField(result, "id", 1L);
        result.setCompletedAt(Instant.now().minusSeconds(60));
        result.setFinishReason(FinishReason.COMPLETED);
        result.setLevelsStats(objectMapper.writeValueAsString(List.of(
                LevelStatsDto.of(includedLevel.getId(), includedLevel.getTitle(), 100, 10, false, true),
                LevelStatsDto.of(excludedLevel.getId(), excludedLevel.getTitle(), 500, 20, false, true)
        )));
        when(resultRepository.findByGameId(GAME_ID)).thenReturn(List.of(result));

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);

        assertThat(leaderboard).hasSize(1);
        LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.timeSeconds()).isEqualTo(110);
        assertThat(entry.levelsStats()).hasSize(2);
        assertThat(entry.levelsStats().get(0).countsInTotals()).isTrue();
        assertThat(entry.levelsStats().get(1).countsInTotals()).isFalse();
        assertThat(entry.levelsStats().get(1).totalSeconds()).isEqualTo(520);
    }

    @Test
    void leaderboardPicksBestSoloAttemptByEffectiveTime() throws Exception {
        // Попытка A быстрее по сохранённому total, но после пересчёта учитываемого
        // времени медленнее попытки B.
        GameResult slowEffective = new GameResult(game, user, 200);
        ReflectionTestUtils.setField(slowEffective, "id", 1L);
        slowEffective.setCompletedAt(Instant.now().minusSeconds(120));
        slowEffective.setFinishReason(FinishReason.COMPLETED);
        slowEffective.setLevelsStats(objectMapper.writeValueAsString(List.of(
                LevelStatsDto.of(includedLevel.getId(), includedLevel.getTitle(), 180, 10, false, true),
                LevelStatsDto.of(excludedLevel.getId(), excludedLevel.getTitle(), 5, 5, false, true)
        )));

        GameResult fastEffective = new GameResult(game, user, 700);
        ReflectionTestUtils.setField(fastEffective, "id", 2L);
        fastEffective.setCompletedAt(Instant.now().minusSeconds(60));
        fastEffective.setFinishReason(FinishReason.COMPLETED);
        fastEffective.setLevelsStats(objectMapper.writeValueAsString(List.of(
                LevelStatsDto.of(includedLevel.getId(), includedLevel.getTitle(), 90, 5, false, true),
                LevelStatsDto.of(excludedLevel.getId(), excludedLevel.getTitle(), 600, 5, false, true)
        )));
        when(resultRepository.findByGameId(GAME_ID)).thenReturn(List.of(slowEffective, fastEffective));

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);

        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).timeSeconds()).isEqualTo(95);
        assertThat(leaderboard.get(0).levelsStats().get(0).totalSeconds()).isEqualTo(95);
    }

    @Test
    void teamLeaderboardExcludesFlaggedLevelFromTotal() throws Exception {
        Team team = new Team();
        team.setId(55L);
        team.setName("Команда");
        TeamGameSession session = new TeamGameSession();
        session.setId(77L);
        session.setGame(game);
        session.setTeam(team);
        session.setStatus(TeamGameSession.STATUS_FINISHED);
        session.setFinishReason(FinishReason.COMPLETED);
        session.setStartTime(Instant.now().minusSeconds(400));
        session.setStartedAt(Instant.now().minusSeconds(400));
        session.setEndedAt(Instant.now().minusSeconds(10));

        TeamLevelStat s1 = new TeamLevelStat();
        s1.setSession(session);
        s1.setLevelId(includedLevel.getId());
        s1.setLevelIndex(0);
        s1.setTitle(includedLevel.getTitle());
        s1.setStartedAt(Instant.now().minusSeconds(300));
        s1.setEndedAt(Instant.now().minusSeconds(200));
        s1.setPureSeconds(100);
        s1.setPenaltySeconds(10);
        s1.setTimedOut(false);

        TeamLevelStat s2 = new TeamLevelStat();
        s2.setSession(session);
        s2.setLevelId(excludedLevel.getId());
        s2.setLevelIndex(1);
        s2.setTitle(excludedLevel.getTitle());
        s2.setStartedAt(Instant.now().minusSeconds(200));
        s2.setEndedAt(Instant.now().minusSeconds(10));
        s2.setPureSeconds(500);
        s2.setPenaltySeconds(20);
        s2.setTimedOut(false);

        when(teamGameSessionRepository.findByGameIdAndStatusInOrderByCreatedAtDesc(eq(GAME_ID), anyList()))
                .thenReturn(List.of(session));
        when(teamLevelStatRepository.findBySessionIdOrderByLevelIndexAsc(77L)).thenReturn(List.of(s1, s2));

        List<LeaderboardEntry> leaderboard = service.leaderboard(GAME_ID);

        assertThat(leaderboard).hasSize(1);
        LeaderboardEntry entry = leaderboard.get(0);
        assertThat(entry.team()).isTrue();
        assertThat(entry.timeSeconds()).isEqualTo(110);
        assertThat(entry.levelsStats()).hasSize(2);
        assertThat(entry.levelsStats().get(0).countsInTotals()).isTrue();
        assertThat(entry.levelsStats().get(1).countsInTotals()).isFalse();
        assertThat(entry.levelsStats().get(1).totalSeconds()).isEqualTo(520);
    }

    @Test
    void createGameDefaultsIncludeTimeInStatsToTrueWhenOmitted() {
        GameRequest request = new GameRequest(
                "Новая игра",
                null,
                10.0,
                3,
                "Описание",
                "PUBLIC",
                List.of(),
                null,
                List.of(new GameRequest.LevelRequest(
                        "Уровень 1",
                        10.0,
                        LevelType.NORMAL,
                        0,
                        List.of(),
                        List.of(),
                        null,
                        List.of(new GameRequest.QuestionRequest("Вопрос", List.of("Ответ"), List.of(), 1)),
                        null,
                        null
                ))
        );
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
            Game g = inv.getArgument(0);
            g.setId(999L);
            long lid = 1L;
            for (GameLevel l : g.getLevels()) {
                if (l.getId() == null) l.setId(lid++);
            }
            return g;
        });

        GameResponse response = service.createGame(request, user);

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertThat(captor.getValue().getLevels()).hasSize(1);
        assertThat(captor.getValue().getLevels().get(0).isIncludeTimeInStats()).isTrue();
        assertThat(response.levels()).hasSize(1);
        assertThat(response.levels().get(0).includeTimeInStats()).isTrue();
    }

    @Test
    void entityAndMigrationDefaultIncludeTimeInStatsToTrue() throws Exception {
        assertThat(new GameLevel().isIncludeTimeInStats()).isTrue();

        Path migration = Path.of("..", "deploy", "add-include-time-in-stats.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("deploy", "add-include-time-in-stats.sql");
        }
        assertThat(Files.exists(migration)).as("migration script exists").isTrue();
        String sql = Files.readString(migration);
        assertThat(sql).contains("include_time_in_stats boolean");
        assertThat(sql).contains("SET include_time_in_stats = TRUE");
        assertThat(sql).contains("SET DEFAULT TRUE");
        assertThat(sql).contains("SET NOT NULL");
    }

    private static GameLevel level(Long id, String title, int order, LevelType type, boolean includeTimeInStats) {
        GameLevel level = new GameLevel();
        level.setId(id);
        level.setTitle(title);
        level.setOrderIndex(order);
        level.setType(type);
        level.setTimeLimitMinutes(10.0);
        level.setIncludeTimeInStats(includeTimeInStats);
        return level;
    }
}
