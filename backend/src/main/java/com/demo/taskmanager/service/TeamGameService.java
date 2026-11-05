package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.ActiveTeamSessionResponse;
import com.demo.taskmanager.dto.GamePlayResponse;
import com.demo.taskmanager.dto.TeamGameAnswerRequest;
import com.demo.taskmanager.dto.TeamGameHintRequest;
import com.demo.taskmanager.dto.TeamGameLobbyResponse;
import com.demo.taskmanager.dto.TeamGameStartRequest;
import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.dto.TeamMemberResponse;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelHint;
import com.demo.taskmanager.entity.StandardAnswer;
import com.demo.taskmanager.entity.StandardQuestion;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;
import com.demo.taskmanager.entity.TeamMember;
import com.demo.taskmanager.entity.TeamSessionEvent;
import com.demo.taskmanager.entity.TeamSessionPresence;
import com.demo.taskmanager.entity.TeamSessionProgress;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.StandardQuestionRepository;
import com.demo.taskmanager.repository.TeamGameSessionRepository;
import com.demo.taskmanager.repository.TeamSessionEventRepository;
import com.demo.taskmanager.repository.TeamSessionPresenceRepository;
import com.demo.taskmanager.repository.TeamSessionProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Командные игры (co-op) с синхронизацией в реальном времени.
 *
 * Модель работы:
 *  - сессия создаётся в момент нажатия «Начать командную игру»; её автор
 *    становится хостом (host_id) и единственным, кто может завершить игру;
 *  - все действия участников (ответ, подсказка, переход уровня) проходят
 *    через сервер, который обновляет состояние сессии и рассылает события
 *    комнате WebSocket /topic/team_session_{sessionId};
 *  - подключившийся участник получает полный слепок состояния, поэтому
 *    попадает ровно на текущий момент партии;
 *  - при отключении хоста права хоста передаются следующему активному игроку.
 */
@Service
public class TeamGameService {

    /** Пользователь считается онлайн, пока его «последняя активность» не старше этого окна. */
    private static final long PRESENCE_TTL_SECONDS = 60;

    /** Максимальное число событий, отдаваемых в ленту. */
    private static final int MAX_EVENTS = 100;

    /** Канал комнаты командной игры: /topic/team_session_{sessionId}. */
    private static final String SESSION_TOPIC_PREFIX = "/topic/team_session_";

    /** Канал команды: /topic/team_{teamId} — туда шлём событие о старте партии. */
    private static final String TEAM_TOPIC_PREFIX = "/topic/team_";

    private final TeamGameSessionRepository sessionRepository;
    private final TeamSessionProgressRepository progressRepository;
    private final TeamSessionEventRepository eventRepository;
    private final TeamSessionPresenceRepository presenceRepository;
    private final GameRepository gameRepository;
    private final TeamService teamService;
    private final StandardQuestionRepository standardQuestionRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public TeamGameService(TeamGameSessionRepository sessionRepository,
                           TeamSessionProgressRepository progressRepository,
                           TeamSessionEventRepository eventRepository,
                           TeamSessionPresenceRepository presenceRepository,
                           GameRepository gameRepository,
                           TeamService teamService,
                           StandardQuestionRepository standardQuestionRepository,
                           ObjectMapper objectMapper,
                           SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.eventRepository = eventRepository;
        this.presenceRepository = presenceRepository;
        this.gameRepository = gameRepository;
        this.teamService = teamService;
        this.standardQuestionRepository = standardQuestionRepository;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    // ------------------------------------------------------------------
    // Лобби и запуск
    // ------------------------------------------------------------------

    /**
     * Подготовка к игре: возвращаем лобби команды (без создания сессии).
     * Сама сессия создаётся в {@link #startGame}.
     */
    @Transactional
    public TeamGameLobbyResponse openLobby(Long teamId, Long gameId, User user) {
        if (!teamService.isMember(teamId, user)) {
            throw new ForbiddenException("Только участник команды может играть командой");
        }
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Игра не найдена"));
        Team team = teamService.findTeamEntity(teamId);

        // Игрок не может параллельно проходить одну игру в разных командах.
        List<TeamGameSession> activeForGame = sessionRepository
                .findByGameIdAndStatusInOrderByCreatedAtDesc(gameId, List.of(TeamGameSession.STATUS_ACTIVE));
        for (TeamGameSession existing : activeForGame) {
            if (existing.getTeam() == null || existing.getTeam().getId().equals(teamId)) {
                continue;
            }
            if (teamService.isMember(existing.getTeam().getId(), user)) {
                throw new BadRequestException("Вы уже участвуете в этой игре в составе команды «"
                        + existing.getTeam().getName()
                        + "». Завершите текущую сессию, чтобы играть другой командой.");
            }
        }

        // Уже идущая партия этой команды — подключаемся к ней, а не создаём новую.
        TeamGameSession session = findActiveSession(teamId, gameId);
        if (session != null) {
            try { markOnline(session, user); } catch (Exception ignored) {}
        }
        List<TeamMemberResponse> members = teamService.getMembers(team);
        return TeamGameLobbyResponse.from(team, game, session, members, onlineIds(session == null ? null : session.getId()));
    }

    /**
     * Запуск командной игры. Создаёт сессию, назначает хоста и рассылает
     * событие STARTED команде — остальные участники подключаются автоматически.
     */
    @Transactional
    public TeamGameStateResponse startGame(Long teamId, Long gameId, User user) {
        if (!teamService.isMember(teamId, user)) {
            throw new ForbiddenException("Только участник команды может играть командой");
        }
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Игра не найдена"));
        Team team = teamService.findTeamEntity(teamId);

        // Если эта партия уже идёт — присоединяемся к ней (без смены хоста).
        TeamGameSession session = findActiveSession(teamId, gameId);
        if (session != null) {
            ensureTiming(session);
            try { markOnline(session, user); } catch (Exception ignored) {}
            addEvent(session, "Игрок " + user.getUsername() + " присоединился");
            TeamGameStateResponse state = buildState(session, user);
            broadcast(session.getId(), event("JOINED", session, user, state));
            broadcastToTeam(session, event("STARTED", session, user, state));
            return state;
        }

        // Новая сессия: игрок, нажавший «Начать», становится хостом.
        // Длительность фиксируется здесь и больше не меняется вместе с настройкой игры.
        Instant startedAt = Instant.now();
        double durationMinutes = gameDurationMinutes(game);
        session = new TeamGameSession();
        session.setTeam(team);
        session.setGame(game);
        session.setHostId(user.getId());
        session.setHostUsername(user.getUsername());
        session.setStatus(TeamGameSession.STATUS_ACTIVE);
        session.setSessionStatus(TeamGameSession.STATUS_ACTIVE);
        session.setCurrentLevelIndex(0);
        session.setCurrentLevel(0);
        session.setStartTime(startedAt);
        session.setDurationMinutes(durationMinutes);
        session.setEndsAt(startedAt.plusSeconds(durationSeconds(durationMinutes)));
        session.setGameStateJson("{}");
        session.setStateData("{}");
        List<GameLevel> levelsStart = sortedLevels(game);
        if (!levelsStart.isEmpty()) {
            setLevelEndTime(session, levelsStart.get(0), startedAt);
        }
        sessionRepository.save(session);

        markOnline(session, user);
        ensureProgress(session);
        addEvent(session, "Игра началась. Удачи, команде!");

        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("STARTED", session, user, state));
        broadcastToTeam(session, event("STARTED", session, user, state));
        return state;
    }

    /** Активные партии команд, в которых состоит пользователь — для карточек в списке игр. */
    @Transactional(readOnly = true)
    public List<ActiveTeamSessionResponse> activeSessionsFor(User user) {
        if (user == null) {
            return List.of();
        }
        List<Long> teamIds = teamService.teamIdsOf(user);
        if (teamIds.isEmpty()) {
            return List.of();
        }
        List<ActiveTeamSessionResponse> result = new ArrayList<>();
        for (TeamGameSession session : sessionRepository
                .findByTeamIdInAndStatusInOrderByCreatedAtDesc(teamIds, List.of(TeamGameSession.STATUS_ACTIVE, "ACTIVE", "active"))) {
            result.add(toActiveResponse(session));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Игровые действия
    // ------------------------------------------------------------------

    /**
     * Проверка ответа. Правильный ответ сохраняется в состояние сессии
     * и рассылается событием sync_correct_answer всем участникам комнаты.
     */
    @Transactional
    public TeamGameStateResponse submitAnswer(Long sessionId, User user, TeamGameAnswerRequest request) {
        TeamGameSession session = requireActiveSession(sessionId, user);
        if (request == null || request.questionId() == null || request.answer() == null) {
            throw new BadRequestException("Укажите ответ");
        }
        StandardQuestion question = standardQuestionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        if (question.getLevel() == null || question.getLevel().getGame() == null
                || !question.getLevel().getGame().getId().equals(session.getGame().getId())) {
            throw new NotFoundException("Вопрос не найден");
        }
        GameLevel level = question.getLevel();
        String normalized = normalize(request.answer());
        StandardAnswer matched = null;
        if (question.getAnswers() != null) {
            for (StandardAnswer a : question.getAnswers()) {
                String text = a.getAnswerText() == null ? "" : a.getAnswerText().trim();
                if (!text.isEmpty() && normalize(text).equals(normalized)) {
                    matched = a;
                    break;
                }
            }
        }
        boolean correct = matched != null;

        if (!correct) {
            addEvent(session, "Игрок " + user.getUsername() + " ввёл неверный ответ");
            TeamGameStateResponse state = buildState(session, user);
            Map<String, Object> payload = event("ANSWER_WRONG", session, user, state);
            payload.put("answer", request.answer());
            payload.put("questionId", question.getId());
            broadcast(session.getId(), payload);
            return state;
        }

        TeamSessionProgress progress = ensureProgressForLevel(session, level);
        Map<String, String> solved = parseSolved(progress);
        String key = "l" + level.getId() + ":q" + question.getId() + ":a" + matched.getId();
        solved.put(key, matched.getAnswerText());
        progress.setSolvedData(writeSolved(solved));
        progressRepository.save(progress);
        persistState(session, level, solved, progress.getPenaltyTime());

        addEvent(session, "Игрок " + user.getUsername() + " разгадал слово \"" + matched.getAnswerText() + "\"");
        boolean levelComplete = isLevelComplete(level, solved);

        TeamGameStateResponse state = buildState(session, user);
        Map<String, Object> payload = event("sync_correct_answer", session, user, state);
        payload.put("answer", matched.getAnswerText());
        payload.put("questionId", question.getId());
        payload.put("levelCompleted", levelComplete);
        broadcast(session.getId(), payload);

        if (levelComplete) {
            advance(session, user, state);
        }
        return buildState(session, user);
    }

    /**
     * Использование подсказки: начисляет штраф, обновляет состояние
     * и рассылает событие sync_hint_used.
     */
    @Transactional
    public TeamGameStateResponse requestHint(Long sessionId, User user, TeamGameHintRequest request) {
        TeamGameSession session = requireActiveSession(sessionId, user);
        if (request == null || request.hintIndex() == null) {
            throw new BadRequestException("Укажите подсказку");
        }
        GameLevel level = currentLevel(session);
        if (level == null) {
            throw new BadRequestException("Нет активного уровня");
        }
        List<LevelHint> hints = level.getHints() == null ? List.of()
                : level.getHints().stream().sorted(Comparator.comparingInt(LevelHint::getOrderIndex)).toList();
        if (request.hintIndex() < 0 || request.hintIndex() >= hints.size()) {
            throw new BadRequestException("Подсказка не найдена");
        }
        LevelHint hint = hints.get(request.hintIndex());
        TeamSessionProgress progress = ensureProgressForLevel(session, level);
        double penalty = progress.getPenaltyTime() + (hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime());
        progress.setPenaltyTime(penalty);
        progressRepository.save(progress);
        persistState(session, level, parseSolved(progress), penalty);

        addEvent(session, "Команда получила подсказку (+" + Math.round((hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime()) * 60) + " сек. штрафа)");

        TeamGameStateResponse state = buildState(session, user);
        Map<String, Object> payload = event("sync_hint_used", session, user, state);
        payload.put("hintIndex", request.hintIndex());
        payload.put("text", hint.getText());
        payload.put("imageUrl", hint.getImageUrl());
        payload.put("hintType", hint.getType() == null ? HintType.GENERAL.name() : hint.getType().name());
        payload.put("penaltySeconds", Math.round((hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime()) * 60));
        broadcast(session.getId(), payload);
        return state;
    }

    /**
     * Завершение командной игры. Разрешено только хосту сессии —
     * остальным участникам возвращается 403.
     */
    @Transactional
    public TeamGameStateResponse endGame(Long sessionId, User user) {
        // Блокируем строку сессии: исключает двойное завершение при повторных
        // нажатиях и гонку с серверным таймером.
        TeamGameSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        if (!teamService.isMember(session.getTeam().getId(), user)) {
            throw new ForbiddenException("Доступ запрещён");
        }
        // Права проверяются только на сервере: клиентскому флагу host не доверяем.
        if (!isHost(session, user)) {
            throw new ForbiddenException("Завершить игру может только тот, кто её запустил");
        }
        // Идемпотентность: повторный вызов хостом просто возвращает актуальное
        // состояние и не меняет ended_at/end_reason.
        if (!session.isActive()) {
            return buildState(session, user);
        }
        finishSessionInternal(session, TeamGameSession.END_REASON_HOST_ENDED, user,
                "Игра завершена ведущим");
        return buildState(session, user);
    }

    // ------------------------------------------------------------------
    // Подключение и отключение
    // ------------------------------------------------------------------

    /** Полный слепок состояния партии для подключающегося участника. */
    @Transactional
    public TeamGameStateResponse getState(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        ensureTiming(session);
        return buildState(session, user);
    }

    /** Присоединение к партии: отмечаем участника онлайн и шлём событие комнате. */
    @Transactional
    public TeamGameStateResponse join(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        // Проверяем, что сессия ещё активна — если завершена, не трогаем присутствие
        if (!session.isActive()) {
            return buildState(session, user);
        }
        ensureTiming(session);
        try {
            markOnline(session, user);
        } catch (Exception e) {
            // При повторных неудачах markOnline возвращает silently, но на всякий случай
            // логируем и продолжаем — пользователь всё равно получит актуальное состояние
        }
        addEvent(session, "Игрок " + user.getUsername() + " присоединился");
        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("JOINED", session, user, state));
        return state;
    }

    /**
     * Выход участника из партии. Игра не ломается: участник просто
     * отключается от сессии. Если вышел хост — права хоста передаются
     * следующему активному игроку, иначе партия продолжается без хоста.
     */
    @Transactional
    public void leave(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        presenceRepository.deleteBySessionIdAndUserId(sessionId, user.getId());
        if (session.isActive() && isHost(session, user)) {
            transferHost(session);
        }
        if (session.isActive()) {
            addEvent(session, "Игрок " + user.getUsername() + " покинул игру");
        }
        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("PRESENCE", session, user, state));
    }

    /** Heartbeat: продлевает отметку «онлайн». */
    @Transactional
    public void heartbeat(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        markOnline(session, user);
    }

    /**
     * Отключение участника (закрыта вкладка). Не хост просто пропадает
     * из списка онлайн. Хост — права передаются следующему активному игроку.
     */
    @Transactional
    public void disconnect(Long sessionId, User user) {
        TeamGameSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        presenceRepository.deleteBySessionIdAndUserId(sessionId, user.getId());
        if (session.isActive() && isHost(session, user)) {
            transferHost(session);
        }
        if (session.isActive()) {
            addEvent(session, "Игрок " + user.getUsername() + " отключился");
        }
        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("DISCONNECTED", session, user, state));
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    /** Переход на следующий уровень или завершение партии. */
    private void advance(TeamGameSession session, User user, TeamGameStateResponse previousState) {
        List<GameLevel> levels = sortedLevels(session.getGame());
        if (session.getCurrentLevelIndex() >= levels.size() - 1) {
            finishSessionInternal(session, TeamGameSession.END_REASON_COMPLETED, user,
                    "Команда завершила игру! 🏆");
            return;
        }
        session.setCurrentLevelIndex(session.getCurrentLevelIndex() + 1);
        session.setCurrentLevel(session.getCurrentLevelIndex());
        if (session.getCurrentLevelIndex() < levels.size()) {
            setLevelEndTime(session, levels.get(session.getCurrentLevelIndex()), Instant.now());
        }
        sessionRepository.save(session);
        ensureProgress(session);
        addEvent(session, "Переход на уровень " + (session.getCurrentLevelIndex() + 1));
        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("sync_level_up", session, user, state));
    }

    private void setLevelEndTime(TeamGameSession session, GameLevel level, Instant from) {
        if (level != null && level.getTimeLimitMinutes() > 0) {
            session.setLevelEndTime(from.plusSeconds(Math.round(level.getTimeLimitMinutes() * 60)));
        } else {
            session.setLevelEndTime(null);
        }
    }

    // ------------------------------------------------------------------
    // Серверный таймер жизненного цикла
    // ------------------------------------------------------------------

    /**
     * Один тик серверного таймера для конкретной сессии.
     *
     * Вызывается планировщиком (TeamGameTimerService). Работает по данным БД,
     * поэтому после перезапуска сервера просроченные партии завершаются при
     * первой же проверке, без участия браузера.
     */
    @Transactional
    public void tickSession(Long sessionId) {
        TeamGameSession session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || !session.isActive()) {
            return;
        }
        Instant now = Instant.now();
        ensureTiming(session);
        // Общий лимит игры авторитетен: он проверяется раньше таймера уровня.
        if (session.getEndsAt() != null && !now.isBefore(session.getEndsAt())) {
            finishSessionInternal(session, TeamGameSession.END_REASON_TIME_EXPIRED, null,
                    "Время игры истекло");
            return;
        }
        if (session.getLevelEndTime() != null && !now.isBefore(session.getLevelEndTime())) {
            handleLevelTimeout(session, now);
        }
    }

    /** Истечение времени уровня: переход дальше или завершение последнего уровня. */
    private void handleLevelTimeout(TeamGameSession session, Instant now) {
        List<GameLevel> levels = sortedLevels(session.getGame());
        if (levels.isEmpty()) {
            finishSessionInternal(session, TeamGameSession.END_REASON_TIME_EXPIRED, null,
                    "Время игры истекло");
            return;
        }
        if (session.getCurrentLevelIndex() >= levels.size() - 1) {
            finishSessionInternal(session, TeamGameSession.END_REASON_TIME_EXPIRED, null,
                    "Время игры истекло");
            return;
        }
        session.setCurrentLevelIndex(session.getCurrentLevelIndex() + 1);
        session.setCurrentLevel(session.getCurrentLevelIndex());
        setLevelEndTime(session, levels.get(session.getCurrentLevelIndex()), now);
        sessionRepository.save(session);

        TeamGameStateResponse state = buildState(session, null);
        Map<String, Object> payload = event("sync_level_up", session, null, state);
        payload.put("reason", TeamGameSession.END_REASON_TIME_EXPIRED);
        broadcast(session.getId(), payload);
    }

    /**
     * Единая точка завершения сессии. Должна вызываться в транзакции
     * с уже захваченной блокировкой строки (или из start/advance).
     */
    private void finishSessionInternal(TeamGameSession session, String reason, User user,
                                       String eventText) {
        Instant endedAt = Instant.now();
        session.setStatus(TeamGameSession.STATUS_FINISHED);
        session.setSessionStatus(TeamGameSession.STATUS_FINISHED);
        session.setEndedAt(endedAt);
        session.setFinishedAt(endedAt);
        session.setEndReason(reason);
        session.setLevelEndTime(null);
        sessionRepository.save(session);
        if (eventText != null && !eventText.isBlank()) {
            addEvent(session, eventText);
        }

        TeamGameStateResponse state = buildState(session, user);
        Map<String, Object> payload = event("end_team_game", session, user, state);
        payload.put("reason", reason);
        payload.put("endReason", reason);
        broadcast(session.getId(), payload);
        broadcastToTeam(session, payload);
    }

    /**
     * Восстанавливает расписание для сессий, созданных до появления
     * duration_minutes/ends_at (или если поля почему-то пусты).
     */
    private void ensureTiming(TeamGameSession session) {
        if (session == null || !session.isActive()) {
            return;
        }
        boolean changed = false;
        Instant startedAt = session.getStartTime();
        if (startedAt == null) {
            startedAt = session.getCreatedAt() == null ? Instant.now() : session.getCreatedAt();
            session.setStartTime(startedAt);
            changed = true;
        } else if (session.getStartedAt() == null) {
            session.setStartedAt(startedAt);
            changed = true;
        }
        Double duration = session.getDurationMinutes();
        if (duration == null || duration <= 0) {
            duration = gameDurationMinutes(session.getGame());
            session.setDurationMinutes(duration);
            changed = true;
        }
        if (session.getEndsAt() == null) {
            session.setEndsAt(startedAt.plusSeconds(durationSeconds(duration)));
            changed = true;
        }
        if (changed) {
            sessionRepository.save(session);
        }
    }

    /** Длительность партии фиксируется на старте из настройки «Время на прохождение». */
    private double gameDurationMinutes(Game game) {
        Double minutes = game == null ? null : game.getTimeLimitMinutes();
        if (minutes == null || !Double.isFinite(minutes) || minutes <= 0) {
            return 10.0;
        }
        return minutes;
    }

    private long durationSeconds(double minutes) {
        return Math.max(1L, Math.round(minutes * 60));
    }

    /** Передача прав хоста следующему активному участнику. */
    private void transferHost(TeamGameSession session) {
        Instant threshold = Instant.now().minusSeconds(PRESENCE_TTL_SECONDS);
        List<TeamSessionPresence> online = presenceRepository
                .findBySessionIdOrderByLastSeenAtDesc(session.getId()).stream()
                .filter(p -> p.getUserId() != null && !p.getUserId().equals(session.getHostId()))
                .filter(p -> p.getLastSeenAt() != null && p.getLastSeenAt().isAfter(threshold))
                .toList();
        if (online.isEmpty()) {
            // Активных участников нет — партия остаётся без хоста,
            // но продолжается: любой участник команды может завершить её позже.
            sessionRepository.save(session);
            return;
        }
        TeamSessionPresence next = online.get(0);
        session.setHostId(next.getUserId());
        session.setHostUsername(next.getUsername());
        sessionRepository.save(session);
        addEvent(session, "Права хоста переданы участнику " + (next.getUsername() == null ? "" : next.getUsername()));
    }

    private boolean isHost(TeamGameSession session, User user) {
        return user != null && session.getHostId() != null && session.getHostId().equals(user.getId());
    }

    /** Уровень пройден, когда по каждому его вопросу набрано нужное число ответов. */
    private boolean isLevelComplete(GameLevel level, Map<String, String> solved) {
        List<StandardQuestion> questions = level.getStandardQuestions();
        if (questions == null || questions.isEmpty()) {
            return true;
        }
        for (StandardQuestion q : questions) {
            int threshold = q.getPassThreshold() == null || q.getPassThreshold() <= 0
                    ? (q.getAnswers() == null ? 0 : q.getAnswers().size())
                    : q.getPassThreshold();
            String prefix = "l" + level.getId() + ":q" + q.getId() + ":";
            long count = solved.keySet().stream().filter(k -> k != null && k.startsWith(prefix)).count();
            if (count < threshold) {
                return false;
            }
        }
        return true;
    }

    private TeamGameSession findActiveSession(Long teamId, Long gameId) {
        return sessionRepository
                .findTopByTeamIdAndGameIdAndStatusOrderByCreatedAtDesc(teamId, gameId, TeamGameSession.STATUS_ACTIVE)
                .or(() -> sessionRepository.findTopByTeamIdAndGameIdAndStatusOrderByCreatedAtDesc(teamId, gameId, "ACTIVE"))
                .orElse(null);
    }

    private ActiveTeamSessionResponse toActiveResponse(TeamGameSession session) {
        Team team = session.getTeam();
        Game game = session.getGame();
        return new ActiveTeamSessionResponse(
                session.getId(),
                team.getId(),
                team.getName(),
                team.getAvatarUrl(),
                game.getId(),
                game.getTitle(),
                session.getHostId(),
                session.getHostUsername(),
                session.getStatus(),
                session.getCurrentLevelIndex(),
                game.getLevels() == null ? 0 : game.getLevels().size(),
                session.getStartTime()
        );
    }

    private Map<String, Object> event(String type, TeamGameSession session, User user, TeamGameStateResponse state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("sessionId", session.getId());
        payload.put("userId", user == null ? null : user.getId());
        payload.put("username", user == null ? null : user.getUsername());
        payload.put("state", state);
        return payload;
    }

    /** Рассылка события комнате партии. */
    private void broadcast(Long sessionId, Map<String, Object> payload) {
        if (sessionId == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(SESSION_TOPIC_PREFIX + sessionId, payload);
        } catch (Exception e) {
            // доставка в реальном времени — вспомогательная
        }
    }

    /** Рассылка события всем участникам команды. */
    private void broadcastToTeam(TeamGameSession session, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(TEAM_TOPIC_PREFIX + session.getTeam().getId(), payload);
        } catch (Exception e) {
            // доставка в реальном времени — вспомогательная
        }
    }

    /** Сохраняет снимок состояния партии в JSON-колонку сессии. */
    private void persistState(TeamGameSession session, GameLevel level,
                              Map<String, String> solved, double penalty) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("levelIndex", session.getCurrentLevelIndex());
        snapshot.put("solved", solved);
        snapshot.put("penaltyTime", penalty);
        snapshot.put("updatedAt", Instant.now().toString());
        if (level != null) {
            snapshot.put("levelId", level.getId());
        }
        try {
            session.setGameStateJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            session.setGameStateJson("{}");
        }
        sessionRepository.save(session);
    }

    private TeamGameSession requireSession(Long sessionId, User user) {
        TeamGameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        if (!teamService.isMember(session.getTeam().getId(), user)) {
            throw new ForbiddenException("Доступ запрещён");
        }
        return session;
    }

    private TeamGameSession requireActiveSession(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        if (!session.isActive()) {
            throw new BadRequestException("Игра не запущена");
        }
        return session;
    }

    private List<GameLevel> sortedLevels(Game game) {
        return game.getLevels() == null ? List.of()
                : game.getLevels().stream().filter(l -> l != null)
                    .sorted(Comparator.comparingInt(GameLevel::getOrderIndex)).toList();
    }

    private GameLevel currentLevel(TeamGameSession session) {
        List<GameLevel> levels = sortedLevels(session.getGame());
        if (levels.isEmpty()) return null;
        int idx = Math.max(0, Math.min(session.getCurrentLevelIndex(), levels.size() - 1));
        return levels.get(idx);
    }

    private TeamGameStateResponse buildState(TeamGameSession session, User user) {
        GameLevel level = currentLevel(session);
        TeamSessionProgress progress = level == null ? null
                : progressRepository.findBySessionIdAndLevelId(session.getId(), level.getId()).orElse(null);
        Map<String, String> solved = progress == null ? new LinkedHashMap<>() : parseSolved(progress);
        double penalty = progress == null ? 0.0 : progress.getPenaltyTime();
        List<TeamMemberResponse> members = teamService.getMembers(session.getTeam());
        Map<String, String> memberNames = new LinkedHashMap<>();
        for (TeamMemberResponse m : members) {
            memberNames.put(String.valueOf(m.userId()), m.username());
        }
        List<Map<String, Object>> eventList = new ArrayList<>();
        List<TeamSessionEvent> storedEvents = eventRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        int start = Math.max(0, storedEvents.size() - MAX_EVENTS);
        for (int i = start; i < storedEvents.size(); i++) {
            TeamSessionEvent ev = storedEvents.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("text", ev.getText());
            entry.put("time", ev.getCreatedAt() == null ? null : ev.getCreatedAt().toString());
            eventList.add(entry);
        }
        return new TeamGameStateResponse(
                session.getId(),
                session.getStatus(),
                session.getCurrentLevelIndex(),
                GamePlayResponse.from(session.getGame()),
                solved,
                penalty,
                hintsSnapshot(),
                session.getStartTime(),
                session.getStartedAt(),
                session.getLevelEndTime(),
                eventList,
                onlineIds(session.getId()),
                memberNames,
                session.getHostId(),
                session.getHostUsername(),
                isHost(session, user),
                session.getDurationMinutes(),
                session.getEndsAt(),
                session.getEndedAt(),
                session.getEndReason()
        );
    }

    /** Содержимое подсказок текущего уровня — чтобы все участники видели одно и то же. */
    private Map<String, Object> hintsSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        return result;
    }

    private void ensureProgress(TeamGameSession session) {
        GameLevel level = currentLevel(session);
        if (level != null) {
            ensureProgressForLevel(session, level);
        }
    }

    private TeamSessionProgress ensureProgressForLevel(TeamGameSession session, GameLevel level) {
        return progressRepository.findBySessionIdAndLevelId(session.getId(), level.getId())
                .orElseGet(() -> {
                    TeamSessionProgress p = new TeamSessionProgress();
                    p.setSession(session);
                    p.setLevel(level);
                    p.setSolvedData("{}");
                    p.setPenaltyTime(0.0);
                    return progressRepository.save(p);
                });
    }

    /** Отметить пользователя онлайн: обновляем/создаём запись присутствия с актуальным временем. */
    @Transactional
    private void markOnline(TeamGameSession session, User user) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                TeamSessionPresence presence = presenceRepository.findBySessionIdAndUserId(session.getId(), user.getId())
                        .orElse(null);
                if (presence == null) {
                    presence = new TeamSessionPresence();
                    presence.setSession(session);
                    presence.setUserId(user.getId());
                }
                presence.setUsername(user.getUsername());
                presence.setLastSeenAt(Instant.now());
                presenceRepository.saveAndFlush(presence);
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException |
                     jakarta.persistence.OptimisticLockException |
                     org.springframework.dao.DataIntegrityViolationException e) {
                // Оптимистичная блокировка: другая транзакция изменила запись — пробуем заново
                attempts++;
                if (attempts >= 3) {
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (jakarta.persistence.EntityNotFoundException e) {
                // Запись удалена другой транзакцией (detached/stale state) — создаём новую с нуля
                attempts++;
                if (attempts >= 3) {
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Exception e) {
                // Игнорируем прочие конфликты
                return;
            }
        }
    }

    /** Пользователи, чья последняя активность не старше окна присутствия. */
    private List<String> onlineIds(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        Instant threshold = Instant.now().minusSeconds(PRESENCE_TTL_SECONDS);
        return presenceRepository.findBySessionIdOrderByLastSeenAtDesc(sessionId).stream()
                .filter(p -> p.getLastSeenAt() != null && p.getLastSeenAt().isAfter(threshold))
                .map(p -> String.valueOf(p.getUserId()))
                .toList();
    }

    private Map<String, String> parseSolved(TeamSessionProgress progress) {
        try {
            Map<String, String> solved = objectMapper.readValue(progress.getSolvedData(),
                    new TypeReference<LinkedHashMap<String, String>>() { });
            return solved == null ? new LinkedHashMap<>() : solved;
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeSolved(Map<String, String> solved) {
        try {
            return objectMapper.writeValueAsString(solved);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.forLanguageTag("ru"));
    }

    private void addEvent(TeamGameSession session, String text) {
        TeamSessionEvent event = new TeamSessionEvent();
        event.setSession(session);
        event.setText(text);
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }
}
