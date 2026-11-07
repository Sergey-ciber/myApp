package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.ActiveTeamSessionResponse;
import com.demo.taskmanager.dto.GamePlayResponse;
import com.demo.taskmanager.dto.TeamGameAnswerRequest;
import com.demo.taskmanager.dto.TeamGameHintRequest;
import com.demo.taskmanager.dto.TeamGameLobbyResponse;
import com.demo.taskmanager.dto.TeamGameStartRequest;
import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.dto.TeamGameStatisticsResponse;
import com.demo.taskmanager.dto.TeamMemberResponse;
import com.demo.taskmanager.dto.TeamPyramidAnswerRequest;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.HintType;
import com.demo.taskmanager.entity.LevelHint;
import com.demo.taskmanager.entity.LevelType;
import com.demo.taskmanager.entity.LevelWord;
import com.demo.taskmanager.entity.StandardAnswer;
import com.demo.taskmanager.entity.StandardQuestion;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;
import com.demo.taskmanager.entity.TeamHintState;
import com.demo.taskmanager.entity.TeamLevelStat;
import com.demo.taskmanager.entity.TeamMember;
import com.demo.taskmanager.entity.TeamQuestionState;
import com.demo.taskmanager.entity.TeamSessionEvent;
import com.demo.taskmanager.entity.TeamSessionPresence;
import com.demo.taskmanager.entity.TeamSessionProgress;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.LevelWordRepository;
import com.demo.taskmanager.repository.StandardQuestionRepository;
import com.demo.taskmanager.repository.TeamGameSessionRepository;
import com.demo.taskmanager.repository.TeamHintStateRepository;
import com.demo.taskmanager.repository.TeamLevelStatRepository;
import com.demo.taskmanager.repository.TeamQuestionStateRepository;
import com.demo.taskmanager.repository.TeamSessionEventRepository;
import com.demo.taskmanager.repository.TeamSessionPresenceRepository;
import com.demo.taskmanager.repository.TeamSessionProgressRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final TeamQuestionStateRepository questionStateRepository;
    private final TeamHintStateRepository hintStateRepository;
    private final TeamLevelStatRepository levelStatRepository;
    private final GameRepository gameRepository;
    private final TeamService teamService;
    private final StandardQuestionRepository standardQuestionRepository;
    private final LevelWordRepository levelWordRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public TeamGameService(TeamGameSessionRepository sessionRepository,
                           TeamSessionProgressRepository progressRepository,
                           TeamSessionEventRepository eventRepository,
                           TeamSessionPresenceRepository presenceRepository,
                           TeamQuestionStateRepository questionStateRepository,
                           TeamHintStateRepository hintStateRepository,
                           TeamLevelStatRepository levelStatRepository,
                           GameRepository gameRepository,
                           TeamService teamService,
                           StandardQuestionRepository standardQuestionRepository,
                           LevelWordRepository levelWordRepository,
                           ObjectMapper objectMapper,
                           SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.eventRepository = eventRepository;
        this.presenceRepository = presenceRepository;
        this.questionStateRepository = questionStateRepository;
        this.hintStateRepository = hintStateRepository;
        this.levelStatRepository = levelStatRepository;
        this.gameRepository = gameRepository;
        this.teamService = teamService;
        this.standardQuestionRepository = standardQuestionRepository;
        this.levelWordRepository = levelWordRepository;
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
            ensureLevelStat(session);
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
        session.setLevelStartedAt(startedAt);
        session.setDurationMinutes(durationMinutes);
        session.setEndsAt(startedAt.plusSeconds(durationSeconds(durationMinutes)));
        session.setGameStateJson("{}");
        session.setStateData("{}");
        List<GameLevel> levelsStart = sortedLevels(game);
        if (!levelsStart.isEmpty()) {
            setLevelEndTime(session, levelsStart.get(0), startedAt);
        }
        sessionRepository.save(session);
        if (!levelsStart.isEmpty()) {
            ensureQuestionStates(session, levelsStart.get(0));
            startLevelStat(session, levelsStart.get(0), startedAt);
        }

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
        // Блокируем сессию: несколько игроков могут отвечать одновременно на разные вопросы.
        TeamGameSession session = lockActiveSession(sessionId, user);
        if (request == null || request.questionId() == null || request.answer() == null) {
            throw new BadRequestException("Укажите ответ");
        }
        StandardQuestion question = standardQuestionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        GameLevel level = currentLevel(session);
        if (level == null || question.getLevel() == null
                || !question.getLevel().getId().equals(level.getId())
                || !level.getGame().getId().equals(session.getGame().getId())) {
            throw new BadRequestException("Вопрос не относится к текущему уровню");
        }
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

        boolean questionSolved = isQuestionSolved(question, solved);
        boolean newlySolved = questionSolved && markQuestionSolved(session, level, question, user);
        // В ленте событий не пишем сам ответ — он виден только в блоке своего вопроса.
        addEvent(session, "Игрок " + user.getUsername() + " разгадал ответ на вопрос №"
                + (question.getOrderIndex() + 1));
        boolean levelComplete = isLevelComplete(level, solved);

        TeamGameStateResponse state = buildState(session, user);
        Map<String, Object> payload = event("sync_correct_answer", session, user, state);
        // Текст ответа не рассылаем: клиент берёт его из своего состояния
        // (questionStates[questionId].answers) и показывает только для выбранного вопроса.
        payload.put("questionId", question.getId());
        payload.put("questionSolved", questionSolved);
        payload.put("levelCompleted", levelComplete);
        broadcast(session.getId(), payload);

        if (newlySolved) {
            // Отдельное событие для выпадающего списка вопросов: статус «решён».
            Map<String, Object> solvedPayload = event("question_solved", session, user, state);
            solvedPayload.put("questionId", question.getId());
            solvedPayload.put("solvedAt", Instant.now().toString());
            broadcast(session.getId(), solvedPayload);
        }

        if (levelComplete) {
            advance(session, user, state);
        }
        return buildState(session, user);
    }

    /**
     * Открытие подсказки по конкретному вопросу.
     *
     * hintId == null — открываем ближайшую по времени доступную подсказку вопроса.
     * hintId задан — открываем именно эту подсказку: её доступность
     * определяется только временем, предыдущие открывать не обязательно.
     * Повторный вызов для уже открытой подсказки идемпотентен: возвращаем
     * актуальное состояние без ошибки и без повторного штрафа.
     */
    @Transactional
    public TeamGameStateResponse revealHint(Long sessionId, User user, Long questionId, Long hintId) {
        TeamGameSession session = lockActiveSession(sessionId, user);
        return revealHintInternal(session, user, questionId, hintId);
    }

    /**
     * Старый контракт /hint: hintIndex — порядковый номер подсказки внутри
     * вопроса. Оставлен для совместимости, внутри использует ту же логику.
     */
    @Transactional
    public TeamGameStateResponse requestHint(Long sessionId, User user, TeamGameHintRequest request) {
        if (request == null || request.questionId() == null) {
            throw new BadRequestException("Укажите вопрос и подсказку");
        }
        TeamGameSession session = lockActiveSession(sessionId, user);
        StandardQuestion question = requireQuestion(session, request.questionId());
        List<LevelHint> hints = hintsForQuestion(currentLevel(session), question);
        Integer index = request.hintIndex();
        if (index == null || index < 0 || index >= hints.size()) {
            throw new BadRequestException("Подсказка не найдена");
        }
        return revealHintInternal(session, user, question.getId(), hints.get(index).getId());
    }

    private TeamGameStateResponse revealHintInternal(TeamGameSession session, User user,
                                                     Long questionId, Long hintId) {
        if (questionId == null) {
            throw new BadRequestException("Укажите вопрос");
        }
        GameLevel level = currentLevel(session);
        if (level == null) {
            throw new BadRequestException("Нет активного уровня");
        }
        StandardQuestion question = requireQuestion(session, questionId, level);
        TeamSessionProgress progress = ensureProgressForLevel(session, level);
        Map<String, String> solved = parseSolved(progress);
        List<LevelHint> hints = hintsForQuestion(level, question);

        // Подсказка из запроса обязана относиться именно к этому вопросу.
        LevelHint requested = null;
        if (hintId != null) {
            requested = hints.stream().filter(h -> hintId.equals(h.getId())).findFirst()
                    .orElseThrow(() -> new BadRequestException("Подсказка не относится к этому вопросу"));
        }

        // Идемпотентность: уже открытая подсказка возвращает состояние как есть,
        // без ошибки, повторного штрафа и повторной записи.
        if (requested != null) {
            TeamHintState existing = hintStateRepository
                    .findBySessionIdAndHintId(session.getId(), requested.getId()).orElse(null);
            if (existing != null && existing.isRevealed()) {
                return buildState(session, user);
            }
        }

        if (isQuestionSolved(question, solved)) {
            throw new BadRequestException("Вопрос уже решён — новые подсказки недоступны");
        }

        if (hints.isEmpty()) {
            throw new BadRequestException("Для этого вопроса нет подсказок");
        }
        Map<Long, TeamHintState> revealed = revealedHints(session, level);
        LevelHint hint;
        if (requested != null) {
            // Явный запрос: открываем именно эту подсказку.
            // Доступность определяется только временем — предыдущие подсказки
            // открывать не обязательно.
            hint = requested;
        } else {
            // Без hintId берём ближайшую по времени ещё не открытую подсказку.
            hint = hints.stream()
                    .filter(h -> h.getId() != null && !isHintRevealed(revealed, h.getId()))
                    .min(Comparator.comparing(h -> hintAvailableAt(session, level, h)))
                    .orElse(null);
            if (hint == null) {
                throw new BadRequestException("Все подсказки по этому вопросу уже получены");
            }
        }
        // Время проверяется только на сервере и отсчитывается от старта уровня.
        Instant availableAt = hintAvailableAt(session, level, hint);
        Instant now = Instant.now();
        if (availableAt != null && now.isBefore(availableAt)) {
            throw new BadRequestException("Подсказка будет доступна через "
                    + formatDelay(java.time.Duration.between(now, availableAt).getSeconds()));
        }

        TeamHintState state = new TeamHintState();
        state.setSession(session);
        state.setTeamId(session.getTeam().getId());
        state.setLevelId(level.getId());
        state.setQuestionId(question.getId());
        state.setHintId(hint.getId());
        state.setRevealed(true);
        state.setRevealedAt(now);
        state.setRevealedByUserId(user.getId());
        hintStateRepository.save(state);

        double penalty = progress.getPenaltyTime()
                + (hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime());
        progress.setPenaltyTime(penalty);
        progressRepository.save(progress);
        persistState(session, level, solved, penalty);

        long penaltySeconds = Math.round((hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime()) * 60);
        addEvent(session, "Игрок " + user.getUsername() + " открыл подсказку по вопросу"
                + (penaltySeconds > 0 ? " (+" + penaltySeconds + " сек. штрафа)" : ""));

        TeamGameStateResponse stateResponse = buildState(session, user);
        Map<String, Object> payload = event("hint_revealed", session, user, stateResponse);
        payload.put("questionId", question.getId());
        payload.put("hintId", hint.getId());
        payload.put("hintIndex", hints.indexOf(hint));
        payload.put("text", hint.getText());
        payload.put("imageUrl", hint.getImageUrl());
        payload.put("mediaUrls", hint.getMediaUrls() == null ? List.of() : hint.getMediaUrls());
        payload.put("hintType", hint.getType() == null ? HintType.GENERAL.name() : hint.getType().name());
        payload.put("penaltySeconds", penaltySeconds);
        broadcast(session.getId(), payload);

        // Совместимость со старым клиентом, который слушал sync_hint_used.
        Map<String, Object> legacy = new HashMap<>(payload);
        legacy.put("type", "sync_hint_used");
        broadcast(session.getId(), legacy);
        return stateResponse;
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

    /**
     * Полный слепок состояния партии для подключающегося участника.
     *
     * Одновременно восстанавливает участие: если пользователь был в игре
     * (перезагрузка страницы, переподключение), его отметка присутствия
     * снова становится актуальной. Сам факт запроса состояния не означает
     * выход из игры и не передаёт права хоста.
     */
    @Transactional
    public TeamGameStateResponse getState(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        ensureTiming(session);
        if (session.isActive()) {
            ensureLevelStat(session);
            try {
                markOnline(session, user);
            } catch (Exception e) {
                // Не влияет на получение состояния — отметка присутствия обновится позже
            }
        }
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
        ensureLevelStat(session);
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
     * Явный выход участника из партии.
     *
     * Только этот сценарий считается выходом из игры:
     * участие помечается завершённым, а если вышел хост — права хоста
     * передаются следующему активному участнику.
     * Кратковременная потеря связи (перезагрузка страницы, обрыв сети)
     * выходом не считается — см. disconnect().
     */
    @Transactional
    public void leave(Long sessionId, User user) {
        // Блокируем строку сессии: передача роли хоста должна быть атомарной,
        // иначе два одновременных выхода могли бы выбрать разных новых хостов.
        TeamGameSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        if (!teamService.isMember(session.getTeam().getId(), user)) {
            throw new ForbiddenException("Доступ запрещён");
        }
        // Участие завершено явно: удаляем отметку присутствия.
        presenceRepository.deleteBySessionIdAndUserId(sessionId, user.getId());
        // Сбрасываем изменения сразу: выбор нового хоста не должен увидеть
        // уходящего участника в списке активных.
        presenceRepository.flush();
        boolean hostLeft = session.isActive() && isHost(session, user);
        Long previousHostId = session.getHostId();
        if (hostLeft) {
            // Передача прав хоста: если других активных участников нет,
            // роль остаётся за прежним хостом (партия продолжается без хоста).
            transferHost(session);
        }
        if (session.isActive()) {
            addEvent(session, "Игрок " + user.getUsername() + " покинул игру");
        }
        TeamGameStateResponse state = buildState(session, user);
        broadcast(session.getId(), event("PRESENCE", session, user, state));
        if (hostLeft) {
            broadcastHostChanged(session, previousHostId, user);
        }
    }

    /** Heartbeat: продлевает отметку «онлайн». */
    @Transactional
    public void heartbeat(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        markOnline(session, user);
    }

    /**
     * Временное отключение участника (закрыта вкладка, обрыв сети,
     * перезагрузка страницы).
     *
     * Это НЕ выход из игры: участие сохраняется, права хоста не передаются.
     * Отметка «онлайн» просто устаревает (PRESENCE_TTL_SECONDS), и после
     * переподключения игрок снова получает актуальное состояние.
     */
    @Transactional
    public void disconnect(Long sessionId, User user) {
        TeamGameSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        // Не удаляем участие: игрок остаётся участником сессии.
        // Отметка присутствия остаётся, но её last_seen_at устаревает.
        if (session.isActive()) {
            addEvent(session, "Игрок " + user.getUsername() + " временно отключился");
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
        Instant levelStartedAt = Instant.now();
        // Уровень решён: фиксируем его чистое и штрафное время до перехода дальше.
        closeCurrentLevelStat(session, levelStartedAt, false);
        session.setCurrentLevelIndex(session.getCurrentLevelIndex() + 1);
        session.setCurrentLevel(session.getCurrentLevelIndex());
        session.setLevelStartedAt(levelStartedAt);
        if (session.getCurrentLevelIndex() < levels.size()) {
            setLevelEndTime(session, levels.get(session.getCurrentLevelIndex()), levelStartedAt);
        }
        sessionRepository.save(session);
        if (session.getCurrentLevelIndex() < levels.size()) {
            // Отсчёт подсказок нового уровня начинается заново.
            ensureQuestionStates(session, levels.get(session.getCurrentLevelIndex()));
            startLevelStat(session, levels.get(session.getCurrentLevelIndex()), levelStartedAt);
        }
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
            // Последний уровень истёк: чистое время равно полному лимиту уровня.
            closeCurrentLevelStat(session, now, true);
            finishSessionInternal(session, TeamGameSession.END_REASON_TIME_EXPIRED, null,
                    "Время игры истекло");
            return;
        }
        // Уровень завершился по таймауту: чистое время равно лимиту уровня.
        closeCurrentLevelStat(session, now, true);
        session.setCurrentLevelIndex(session.getCurrentLevelIndex() + 1);
        session.setCurrentLevel(session.getCurrentLevelIndex());
        session.setLevelStartedAt(now);
        setLevelEndTime(session, levels.get(session.getCurrentLevelIndex()), now);
        sessionRepository.save(session);
        ensureQuestionStates(session, levels.get(session.getCurrentLevelIndex()));
        startLevelStat(session, levels.get(session.getCurrentLevelIndex()), now);

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
        // Закрываем текущий уровень по фактическому времени (но не больше лимита):
        // так статистика остаётся корректной и при завершении игры хостом.
        closeCurrentLevelStat(session, endedAt, false);
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

    // ------------------------------------------------------------------
    // Статистика времени по уровням
    // ------------------------------------------------------------------

    /**
     * Серверная статистика времени по уровням партии.
     *
     * Данные берутся только из БД (team_level_stats), поэтому одинаковы для
     * всех участников и переживают перезагрузку страницы. Уровни, которые
     * не начинались, в ответ не попадают.
     */
    @Transactional(readOnly = true)
    public TeamGameStatisticsResponse getStatistics(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        List<TeamLevelStat> stored = levelStatRepository
                .findBySessionIdOrderByLevelIndexAsc(session.getId());
        Map<Long, TeamLevelStat> byLevelId = new LinkedHashMap<>();
        for (TeamLevelStat stat : stored) {
            if (stat.getLevelId() != null) {
                byLevelId.put(stat.getLevelId(), stat);
            }
        }

        List<TeamGameStatisticsResponse.LevelStat> levels = new ArrayList<>();
        long totalSeconds = 0;
        for (GameLevel level : sortedLevels(session.getGame())) {
            TeamLevelStat stat = byLevelId.get(level.getId());
            // Уровень не начинался или ещё идёт — в статистику не включаем.
            if (stat == null || stat.getEndedAt() == null) {
                continue;
            }
            long pure = Math.max(0, stat.getPureSeconds());
            long penalty = Math.max(0, stat.getPenaltySeconds());
            long totalLevel = pure + penalty;
            String title = stat.getTitle() == null || stat.getTitle().isBlank()
                    ? level.getTitle() : stat.getTitle();
            levels.add(new TeamGameStatisticsResponse.LevelStat(
                    level.getId(),
                    stat.getLevelIndex(),
                    title,
                    stat.getStartedAt(),
                    stat.getEndedAt(),
                    pure,
                    penalty,
                    totalLevel,
                    stat.isTimedOut()
            ));
            totalSeconds += totalLevel;
        }

        return new TeamGameStatisticsResponse(
                session.getId(),
                session.getStatus(),
                session.getEndReason(),
                session.getStartTime(),
                session.getEndedAt(),
                totalSeconds,
                levels
        );
    }

    /** Создаёт запись времени уровня, если её ещё нет (например, для старых сессий). */
    private void ensureLevelStat(TeamGameSession session) {
        if (session == null || !session.isActive()) {
            return;
        }
        GameLevel level = currentLevel(session);
        if (level == null || level.getId() == null) {
            return;
        }
        if (levelStatRepository.findBySessionIdAndLevelId(session.getId(), level.getId()).isPresent()) {
            return;
        }
        startLevelStat(session, level, levelAnchor(session));
    }

    /** Начинает отсчёт уровня: запись создаётся один раз. */
    private void startLevelStat(TeamGameSession session, GameLevel level, Instant startedAt) {
        if (session == null || level == null || level.getId() == null) {
            return;
        }
        if (levelStatRepository.findBySessionIdAndLevelId(session.getId(), level.getId()).isPresent()) {
            return;
        }
        Instant started = startedAt != null ? startedAt : levelAnchor(session);
        TeamLevelStat stat = new TeamLevelStat();
        stat.setSession(session);
        stat.setLevelId(level.getId());
        stat.setLevelIndex(session.getCurrentLevelIndex());
        stat.setTitle(level.getTitle());
        stat.setStartedAt(started);
        stat.setPureSeconds(0);
        stat.setPenaltySeconds(0);
        stat.setTimedOut(false);
        levelStatRepository.save(stat);
    }

    /**
     * Закрывает текущий уровень и сохраняет его время.
     *
     * timedOut=true — уровень истёк по своему лимиту, чистое время равно
     * полному лимиту уровня. Иначе чистое время равно фактическому времени
     * прохождения и ограничивается лимитом уровня.
     * Повторный вызов для уже закрытого уровня ничего не меняет.
     */
    private void closeCurrentLevelStat(TeamGameSession session, Instant endedAt, boolean timedOut) {
        GameLevel level = currentLevel(session);
        if (level == null || level.getId() == null) {
            return;
        }
        TeamLevelStat stat = levelStatRepository
                .findBySessionIdAndLevelId(session.getId(), level.getId())
                .orElseGet(() -> {
                    TeamLevelStat created = new TeamLevelStat();
                    created.setSession(session);
                    created.setLevelId(level.getId());
                    created.setLevelIndex(session.getCurrentLevelIndex());
                    created.setTitle(level.getTitle());
                    created.setStartedAt(levelAnchor(session));
                    return created;
                });
        if (stat.getEndedAt() != null) {
            return;
        }
        Instant started = stat.getStartedAt() != null ? stat.getStartedAt() : levelAnchor(session);
        Instant end = endedAt != null ? endedAt : Instant.now();
        long limit = levelLimitSeconds(level);
        long elapsed = Math.max(0, Duration.between(started, end).getSeconds());
        long pure = timedOut && limit > 0 ? limit : (limit > 0 ? Math.min(elapsed, limit) : elapsed);

        stat.setStartedAt(started);
        stat.setEndedAt(end);
        stat.setPureSeconds(pure);
        stat.setPenaltySeconds(penaltySecondsOf(session, level));
        stat.setTimedOut(timedOut);
        levelStatRepository.save(stat);
    }

    /** Суммарный штраф уровня в секундах; источник — прогресс уровня (минуты). */
    private long penaltySecondsOf(TeamGameSession session, GameLevel level) {
        return progressRepository.findBySessionIdAndLevelId(session.getId(), level.getId())
                .map(p -> Math.max(0L, Math.round(p.getPenaltyTime() * 60)))
                .orElse(0L);
    }

    /** Лимит уровня в секундах; 0 — уровень без ограничения по времени. */
    private long levelLimitSeconds(GameLevel level) {
        if (level == null || level.getTimeLimitMinutes() <= 0) {
            return 0L;
        }
        return Math.max(0L, Math.round(level.getTimeLimitMinutes() * 60));
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
        if (session.getLevelStartedAt() == null) {
            // Для сессий, созданных до появления поля, восстанавливаем старт уровня.
            GameLevel level = currentLevel(session);
            Instant levelStarted = null;
            if (session.getLevelEndTime() != null && level != null && level.getTimeLimitMinutes() > 0) {
                levelStarted = session.getLevelEndTime()
                        .minusSeconds(Math.round(level.getTimeLimitMinutes() * 60));
            } else if (session.getStartTime() != null) {
                levelStarted = session.getStartTime();
            }
            session.setLevelStartedAt(levelStarted);
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

    /**
     * Передача прав хоста следующему активному участнику.
     *
     * Правило выбора (детерминированное): новым хостом становится участник
     * с самым ранним joined_at — то есть тот, кто присоединился раньше всех
     * из оставшихся активных. Список отсортирован, поэтому при двух
     * одновременных выходах результат одинаков для всех.
     *
     * Если активных участников не осталось — роль хоста остаётся за прежним
     * пользователем: партия продолжается, завершить её сможет и он.
     *
     * Вызывается под блокировкой строки сессии — гонки исключены.
     */
    private void transferHost(TeamGameSession session) {
        Instant threshold = Instant.now().minusSeconds(PRESENCE_TTL_SECONDS);
        TeamSessionPresence next = presenceRepository
                .findBySessionIdOrderByJoinedAtAsc(session.getId()).stream()
                .filter(p -> p.getUserId() != null && !p.getUserId().equals(session.getHostId()))
                .filter(p -> p.getLastSeenAt() != null && p.getLastSeenAt().isAfter(threshold))
                .min(Comparator.comparing(p -> p.getJoinedAt() != null ? p.getJoinedAt() : p.getLastSeenAt()))
                .orElse(null);
        if (next == null) {
            // Активных участников нет — роль хоста остаётся за прежним хостом.
            sessionRepository.save(session);
            return;
        }
        session.setHostId(next.getUserId());
        session.setHostUsername(next.getUsername());
        sessionRepository.save(session);
        addEvent(session, "Права хоста переданы участнику " + (next.getUsername() == null ? "" : next.getUsername()));
    }

    /**
     * Событие смены хоста — клиенты обновляют кнопку «Завершить игру».
     * Флаг isHost рассчитывается сервером для каждого участника отдельно.
     */
    private void broadcastHostChanged(TeamGameSession session, Long previousHostId, User actor) {
        if (session == null) {
            return;
        }
        TeamGameStateResponse state = buildState(session, actor);
        Map<String, Object> payload = event("host_changed", session, actor, state);
        payload.put("previousHostId", previousHostId);
        payload.put("newHostId", session.getHostId());
        payload.put("newHostUsername", session.getHostUsername());
        broadcast(session.getId(), payload);
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
        // Флаги host по каждому участнику: считаются сервером от host_id.
        // Клиент берёт свой флаг по своему user_id — роль не вычисляется
        // на клиенте и не зависит от того, кто совершил действие.
        payload.put("isHostByUser", hostFlags(session));
        payload.put("hostId", session.getHostId());
        payload.put("hostUsername", session.getHostUsername());
        return payload;
    }

    /**
     * Авторитетные флаги «я — host» для всех участников сессии.
     *
     * Ключ — id пользователя, значение — является ли он текущим владельцем.
     * Формируется сервером из host_id, поэтому один и тот же broadcast
     * корректно интерпретируется каждым клиентом по-своему.
     */
    private Map<String, Boolean> hostFlags(TeamGameSession session) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        if (session == null) {
            return flags;
        }
        Long hostId = session.getHostId();
        for (TeamMemberResponse member : teamService.getMembers(session.getTeam())) {
            if (member.userId() != null) {
                flags.put(String.valueOf(member.userId()), hostId != null && hostId.equals(member.userId()));
            }
        }
        return flags;
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

    /**
     * Как {@link #requireActiveSession}, но дополнительно блокирует строку сессии
     * на время транзакции. Это защищает от гонок, когда несколько игроков
     * одновременно открывают подсказки или отвечают на разные вопросы.
     */
    private TeamGameSession lockActiveSession(Long sessionId, User user) {
        TeamGameSession session = requireActiveSession(sessionId, user);
        return sessionRepository.findByIdForUpdate(session.getId())
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
    }

    /** Вопрос должен существовать и относиться к текущему уровню сессии. */
    private StandardQuestion requireQuestion(TeamGameSession session, Long questionId) {
        return requireQuestion(session, questionId, currentLevel(session));
    }

    private StandardQuestion requireQuestion(TeamGameSession session, Long questionId, GameLevel level) {
        if (questionId == null) {
            throw new BadRequestException("Укажите вопрос");
        }
        if (level == null) {
            throw new BadRequestException("Нет активного уровня");
        }
        StandardQuestion question = standardQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Вопрос не найден"));
        if (question.getLevel() == null || !question.getLevel().getId().equals(level.getId())
                || level.getGame() == null || session.getGame() == null
                || !level.getGame().getId().equals(session.getGame().getId())) {
            throw new BadRequestException("Вопрос не относится к текущему уровню");
        }
        return question;
    }

    /** Человекочитаемый остаток времени вида «02:15». */
    private String formatDelay(long seconds) {
        long safe = Math.max(0L, seconds);
        long minutes = safe / 60;
        long rest = safe % 60;
        return String.format("%02d:%02d", minutes, rest);
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
                // Текст неоткрытых подсказок не отдаём клиенту даже в описании игры.
                redactUnrevealedHints(session, GamePlayResponse.from(session.getGame())),
                // В общей карте оставляем только ключи (по ним считаются угаданные
                // ответы). Тексты ответов доступны строго по вопросу —
                // в questionStates[questionId].answers.
                redactSolvedTexts(solved),
                penalty,
                hintsSnapshot(),
                questionStates(session, level, solved),
                session.getStartTime(),
                session.getStartedAt(),
                levelAnchor(session),
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
        return new LinkedHashMap<>();
    }

    /**
     * Состояние всех вопросов текущего уровня: решение, открытые подсказки,
     * доступность следующей. Ключ — id вопроса.
     */
    private Map<String, Object> questionStates(TeamGameSession session, GameLevel level,
                                               Map<String, String> solved) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (level == null) {
            return result;
        }
        Map<Long, TeamHintState> revealed = revealedHints(session, level);
        Map<Long, TeamQuestionState> stored = new HashMap<>();
        for (TeamQuestionState state : questionStateRepository
                .findBySessionIdAndLevelId(session.getId(), level.getId())) {
            if (state.getQuestionId() != null) {
                stored.put(state.getQuestionId(), state);
            }
        }
        for (StandardQuestion question : sortedQuestions(level)) {
            List<LevelHint> hints = hintsForQuestion(level, question);
            boolean solvedQuestion = isQuestionSolved(question, solved);
            List<Map<String, Object>> hintViews = new ArrayList<>();
            List<Long> revealedIds = new ArrayList<>();
            // Ближайшая по времени ещё недоступная подсказка (для обратного отсчёта)
            // и список доступных, но ещё не открытых.
            LevelHint nextHint = null;
            Instant nextAvailableAt = null;
            List<Long> availableHintIds = new ArrayList<>();
            Instant serverNow = Instant.now();
            for (LevelHint hint : hints) {
                TeamHintState state = hint.getId() == null ? null : revealed.get(hint.getId());
                boolean isRevealed = state != null && state.isRevealed();
                Instant availableAt = hintAvailableAt(session, level, hint);
                // Доступность зависит только от времени, а не от порядка получения.
                boolean isAvailable = availableAt == null || !serverNow.isBefore(availableAt);
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", hint.getId());
                view.put("orderIndex", hint.getOrderIndex());
                view.put("type", hint.getType() == null ? HintType.GENERAL.name() : hint.getType().name());
                view.put("triggerTime", hint.getTriggerTime());
                view.put("penaltyTime", hint.getPenaltyTime());
                view.put("availableAt", availableAt);
                view.put("isAvailable", isAvailable);
                view.put("isRevealed", isRevealed);
                view.put("revealedAt", state == null ? null : state.getRevealedAt());
                view.put("revealedByUserId", state == null ? null : state.getRevealedByUserId());
                if (isRevealed) {
                    view.put("text", hint.getText());
                    view.put("imageUrl", hint.getImageUrl());
                    view.put("mediaUrls", hint.getMediaUrls() == null ? List.of() : List.copyOf(hint.getMediaUrls()));
                    if (hint.getId() != null) {
                        revealedIds.add(hint.getId());
                    }
                } else {
                    // Текст неоткрытой подсказки не покидает сервер.
                    view.put("text", null);
                    view.put("imageUrl", null);
                    view.put("mediaUrls", List.of());
                    if (!solvedQuestion) {
                        if (isAvailable) {
                            // Подсказку уже можно получить — даже если предыдущие не открыты.
                            if (hint.getId() != null) {
                                availableHintIds.add(hint.getId());
                            }
                        } else if (nextHint == null
                                || availableAt.isBefore(nextAvailableAt)) {
                            // Ближайшая по времени недоступная подсказка — для отсчёта.
                            nextHint = hint;
                            nextAvailableAt = availableAt;
                        }
                    }
                }
                hintViews.add(view);
            }
            TeamQuestionState storedState = stored.get(question.getId());
            boolean isSolved = solvedQuestion || (storedState != null && storedState.isSolved());
            // Правильные ответы отдаём строго по вопросу: клиент показывает
            // только те, что относятся к выбранному вопросу.
            List<String> questionAnswers = answersOfQuestion(solved, question);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("questionId", question.getId());
            view.put("levelId", level.getId());
            view.put("orderIndex", question.getOrderIndex());
            view.put("isSolved", isSolved);
            view.put("solvedAt", storedState == null ? null : storedState.getSolvedAt());
            view.put("solvedByUserId", storedState == null ? null : storedState.getSolvedByUserId());
            view.put("matchedCount", matchedCount(question, solved));
            view.put("passThreshold", passThreshold(question));
            // Угаданные ответы этого вопроса (пустой список, если ещё ничего не угадано).
            view.put("answers", questionAnswers);
            view.put("hintCount", hints.size());
            view.put("revealedHintIds", revealedIds);
            view.put("hints", hintViews);
            view.put("nextHintId", isSolved || nextHint == null ? null : nextHint.getId());
            view.put("nextHintAvailableAt", isSolved || nextHint == null ? null : nextAvailableAt);
            // Подсказки, которые уже можно получить (по времени), — по ним рисуем кнопки.
            view.put("availableHintIds", isSolved ? List.of() : availableHintIds);
            view.put("allHintsRevealed", !hints.isEmpty() && revealedIds.size() >= hints.size());
            result.put(String.valueOf(question.getId()), view);
        }
        return result;
    }

    /** Подсказки вопроса: строго по порядку; legacy-подсказки уровня относим к первому вопросу. */
    private List<LevelHint> hintsForQuestion(GameLevel level, StandardQuestion question) {
        if (level == null || question == null || level.getHints() == null) {
            return List.of();
        }
        List<LevelHint> all = level.getHints().stream()
                .filter(h -> h != null)
                .sorted(Comparator.comparingInt(LevelHint::getOrderIndex))
                .toList();
        List<LevelHint> bound = all.stream()
                .filter(h -> h.getQuestion() != null && question.getId().equals(h.getQuestion().getId()))
                .toList();
        if (!bound.isEmpty()) {
            return bound;
        }
        List<StandardQuestion> questions = sortedQuestions(level);
        boolean first = !questions.isEmpty() && question.getId().equals(questions.get(0).getId());
        return first ? all.stream().filter(h -> h.getQuestion() == null).toList() : List.of();
    }

    private List<StandardQuestion> sortedQuestions(GameLevel level) {
        if (level == null || level.getStandardQuestions() == null) {
            return List.of();
        }
        return level.getStandardQuestions().stream()
                .filter(q -> q != null)
                .sorted(Comparator.comparingInt(StandardQuestion::getOrderIndex))
                .toList();
    }

    private int passThreshold(StandardQuestion question) {
        Integer threshold = question.getPassThreshold();
        if (threshold != null && threshold > 0) {
            return threshold;
        }
        int answers = question.getAnswers() == null ? 0 : question.getAnswers().size();
        return Math.max(1, answers);
    }

    private int matchedCount(StandardQuestion question, Map<String, String> solved) {
        if (question == null || solved == null) {
            return 0;
        }
        String prefix = questionKeyPrefix(question);
        return (int) solved.keySet().stream()
                .filter(key -> key != null && key.startsWith(prefix))
                .count();
    }

    /**
     * Угаданные ответы конкретного вопроса — без ответов других вопросов.
     *
     * Ключи карты solved имеют вид l{levelId}:q{questionId}:a{answerId},
     * поэтому ответы фильтруются по префиксу вопроса.
     */
    private List<String> answersOfQuestion(Map<String, String> solved, StandardQuestion question) {
        if (solved == null || solved.isEmpty() || question == null) {
            return List.of();
        }
        String prefix = questionKeyPrefix(question);
        List<String> answers = new ArrayList<>();
        for (Map.Entry<String, String> entry : solved.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith(prefix)) {
                continue;
            }
            String text = entry.getValue() == null ? "" : entry.getValue().trim();
            if (text.isEmpty()) {
                continue;
            }
            boolean duplicate = answers.stream()
                    .anyMatch(a -> a.equalsIgnoreCase(text));
            if (!duplicate) {
                answers.add(text);
            }
        }
        return answers;
    }

    private String questionKeyPrefix(StandardQuestion question) {
        Long levelId = question.getLevel() == null ? null : question.getLevel().getId();
        return "l" + levelId + ":q" + question.getId() + ":";
    }

    private boolean isQuestionSolved(StandardQuestion question, Map<String, String> solved) {
        return matchedCount(question, solved) >= passThreshold(question);
    }

    /** Время, когда подсказка становится доступной: старт уровня + trigger_time. */
    private Instant hintAvailableAt(TeamGameSession session, GameLevel level, LevelHint hint) {
        Instant anchor = levelAnchor(session);
        double trigger = hint.getTriggerTime() == null ? 0.0 : hint.getTriggerTime();
        if (trigger <= 0) {
            return anchor;
        }
        return anchor.plusSeconds(Math.max(1L, Math.round(trigger * 60)));
    }

    private Instant levelAnchor(TeamGameSession session) {
        if (session.getLevelStartedAt() != null) {
            return session.getLevelStartedAt();
        }
        if (session.getStartTime() != null) {
            return session.getStartTime();
        }
        return Instant.now();
    }

    private Map<Long, TeamHintState> revealedHints(TeamGameSession session, GameLevel level) {
        Map<Long, TeamHintState> result = new HashMap<>();
        for (TeamHintState state : hintStateRepository.findBySessionIdAndLevelId(session.getId(), level.getId())) {
            if (state.isRevealed() && state.getHintId() != null) {
                result.put(state.getHintId(), state);
            }
        }
        return result;
    }

    private boolean isHintRevealed(Map<Long, TeamHintState> revealed, Long hintId) {
        TeamHintState state = revealed.get(hintId);
        return state != null && state.isRevealed();
    }

    /**
     * Убирает тексты правильных ответов из общей карты solved.
     *
     * Ключи сохраняются (l{level}:q{question}:a{answer}), чтобы клиент мог
     * посчитать угаданные ответы, но сами ответы видны только в блоке
     * выбранного вопроса — в questionStates[questionId].answers.
     *
     * Для уровня «Игра в ассоциации» ключи имеют вид {column}:{position}.
     * Текст уже разгаданного слова не является секретом: он нужен всем
     * участникам команды, чтобы восстановить заполненное поле после
     * перезагрузки и синхронизации. Неразгаданные слова в solved не попадают.
     */
    private Map<String, String> redactSolvedTexts(Map<String, String> solved) {
        Map<String, String> result = new LinkedHashMap<>();
        if (solved == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : solved.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (key.matches("\\d+:\\d+")) {
                result.put(key, entry.getValue() == null ? "" : entry.getValue());
            } else {
                result.put(key, "");
            }
        }
        return result;
    }

    /** Не отдаём текст неоткрытых подсказок в общем описании игры. */
    private GamePlayResponse redactUnrevealedHints(TeamGameSession session, GamePlayResponse game) {
        List<GamePlayResponse.GameLevelView> levels = game.levels();
        if (levels == null || levels.isEmpty()) {
            return game;
        }
        Set<Long> revealedIds = new HashSet<>();
        for (TeamHintState state : hintStateRepository.findBySessionIdAndRevealedTrue(session.getId())) {
            if (state.getHintId() != null) {
                revealedIds.add(state.getHintId());
            }
        }
        List<GamePlayResponse.GameLevelView> redacted = levels.stream()
                .map(level -> {
                    List<GamePlayResponse.GameHintView> hints = level.hints() == null ? List.of()
                            : level.hints().stream()
                                .map(hint -> revealedIds.contains(hint.id())
                                        ? hint
                                        : new GamePlayResponse.GameHintView(hint.id(), hint.type(), null, null,
                                                hint.triggerTime(), hint.penaltyTime(), hint.orderIndex(),
                                                hint.questionId(), List.of()))
                                .toList();
                    return new GamePlayResponse.GameLevelView(level.id(), level.title(), level.type(),
                            level.timeLimitMinutes(), hints, level.questions(), level.startWords(), level.mediaUrls());
                })
                .toList();
        return new GamePlayResponse(game.id(), game.title(), game.gameDate(), game.createdAt(), game.active(),
                game.creatorId(), game.creatorUsername(), game.timeLimitMinutes(), game.difficulty(),
                game.description(), game.mediaUrls(), game.startWords(), game.levelId(), game.levelType(),
                game.questions(), game.hintTriggerMinutes(), redacted);
    }

    /** Создаёт строки состояния вопросов при входе на уровень (idempotent). */
    private void ensureQuestionStates(TeamGameSession session, GameLevel level) {
        if (level == null) {
            return;
        }
        for (StandardQuestion question : sortedQuestions(level)) {
            TeamQuestionState state = questionStateRepository
                    .findBySessionIdAndQuestionId(session.getId(), question.getId()).orElse(null);
            if (state == null) {
                state = new TeamQuestionState();
                state.setSession(session);
                state.setTeamId(session.getTeam().getId());
                state.setLevelId(level.getId());
                state.setQuestionId(question.getId());
                state.setSolved(false);
                questionStateRepository.save(state);
            }
        }
    }

    /** Фиксирует решение вопроса; возвращает true, только если оно записано впервые. */
    private boolean markQuestionSolved(TeamGameSession session, GameLevel level,
                                       StandardQuestion question, User user) {
        TeamQuestionState state = questionStateRepository
                .findBySessionIdAndQuestionId(session.getId(), question.getId())
                .orElseGet(() -> {
                    TeamQuestionState created = new TeamQuestionState();
                    created.setSession(session);
                    created.setTeamId(session.getTeam().getId());
                    created.setLevelId(level.getId());
                    created.setQuestionId(question.getId());
                    return created;
                });
        if (state.isSolved()) {
            return false;
        }
        state.setSolved(true);
        state.setSolvedAt(Instant.now());
        state.setSolvedByUserId(user == null ? null : user.getId());
        questionStateRepository.save(state);
        return true;
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
                    presence.setJoinedAt(Instant.now());
                } else if (presence.getJoinedAt() == null) {
                    // Для записей, созданных до появления поля: восстанавливаем
                    // момент присоединения из последней активности.
                    presence.setJoinedAt(presence.getLastSeenAt());
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

    /**
     * Ответ для уровня типа «Игра в ассоциации» (пирамида).
     * Проверяет ответ по колонке/позиции и рассылает результат всем участникам.
     */
    @Transactional
    public TeamGameStateResponse submitPyramidAnswer(Long sessionId, User user, TeamPyramidAnswerRequest request) {
        TeamGameSession session = lockActiveSession(sessionId, user);
        if (request == null || request.column() == null || request.position() == null) {
            throw new BadRequestException("Укажите колонку и позицию");
        }
        GameLevel level = currentLevel(session);
        if (level == null) {
            throw new BadRequestException("Нет активного уровня");
        }
        // Поддерживаем только ASSOCIATIONS уровни для pyramid answer
        if (level.getType() != LevelType.ASSOCIATIONS) {
            throw new BadRequestException("Пирамидный ответ доступен только для уровня «Игра в ассоциации»");
        }
        int column = request.column();
        int position = request.position();
        if (column < 2 || column > 5) {
            throw new BadRequestException("Проверять можно только ответы колонок 2-5");
        }
        // Размеры колонок: 16, 8, 4, 2, 1
        int[] columnSizes = {0, 16, 8, 4, 2, 1};
        if (position < 1 || position > columnSizes[column]) {
            throw new BadRequestException("В колонке " + column + " нет ответа " + position);
        }

        // Получаем правильный ответ из LevelWord (колонка 2-5 — ответы пирамиды)
        List<LevelWord> words = levelWordRepository.findByLevelIdOrderByColumnNumAscPositionAsc(level.getId());
        String expected = null;
        if (words != null) {
            // Ищем слово по колонке и позиции
            for (LevelWord word : words) {
                if (word.getColumnNum() == column
                        && word.getPosition() == position) {
                    expected = word.getWord();
                    break;
                }
            }
        }
        if (expected == null) {
            throw new BadRequestException("Ответ для указанной ячейки не найден");
        }

        String normalized = normalize(request.answer());
        boolean correct = normalize(expected).equals(normalized);

        if (!correct) {
            addEvent(session, "Игрок " + user.getUsername() + " ввёл неверный ответ");
            TeamGameStateResponse state = buildState(session, user);
            Map<String, Object> payload = event("ANSWER_WRONG", session, user, state);
            payload.put("answer", request.answer());
            payload.put("column", column);
            payload.put("position", position);
            broadcast(session.getId(), payload);
            return state;
        }

        // Правильный ответ: сохраняем в solved map. Текст слова нужен всем
        // участникам команды, чтобы поле оставалось заполненным после
        // перезагрузки и при синхронизации по WebSocket.
        TeamSessionProgress progress = ensureProgressForLevel(session, level);
        Map<String, String> solved = parseSolved(progress);
        String key = column + ":" + position;
        boolean alreadySolved = solved.containsKey(key);
        solved.put(key, expected);
        progress.setSolvedData(writeSolved(solved));
        progressRepository.save(progress);
        persistState(session, level, solved, progress.getPenaltyTime());

        if (!alreadySolved) {
            addEvent(session, "Игрок " + user.getUsername() + " разгадал слово в колонке " + column);
        }

        // Рассылаем событие о правильном ответе
        TeamGameStateResponse state = buildState(session, user);
        Map<String, Object> payload = event("sync_correct_answer", session, user, state);
        payload.put("column", column);
        payload.put("position", position);
        payload.put("answer", expected);
        broadcast(session.getId(), payload);

        // Уровень «Игра в ассоциации» пройден, когда разгадана вершина
        // пирамиды — ячейка 5:1. Повторный ответ по уже разгаданной ячейке
        // не должен переводить уровень ещё раз.
        if (!alreadySolved && solved.containsKey("5:1")) {
            advance(session, user, state);
        }

        // Возвращаем актуальное состояние: после перехода на следующий
        // уровень или завершения игры клиент должен получить новый слепок.
        return buildState(session, user);
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
