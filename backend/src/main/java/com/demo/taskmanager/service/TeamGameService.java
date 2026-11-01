package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.GamePlayResponse;
import com.demo.taskmanager.dto.TeamGameAnswerRequest;
import com.demo.taskmanager.dto.TeamGameHintRequest;
import com.demo.taskmanager.dto.TeamGameLobbyResponse;
import com.demo.taskmanager.dto.TeamGameStateResponse;
import com.demo.taskmanager.dto.TeamMemberResponse;
import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.GameLevel;
import com.demo.taskmanager.entity.LevelHint;
import com.demo.taskmanager.entity.StandardAnswer;
import com.demo.taskmanager.entity.StandardQuestion;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;
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

@Service
public class TeamGameService {

    /** Пользователь считается онлайн, пока его «последняя активность» не старше этого окна. */
    private static final long PRESENCE_TTL_SECONDS = 60;

    /** Максимальное число событий, отдаваемых в ленту. */
    private static final int MAX_EVENTS = 100;

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

    private void broadcast(Long sessionId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/team_session_" + sessionId, payload);
    }

    @Transactional
    public TeamGameLobbyResponse createOrGetLobby(Long teamId, Long gameId, User user) {
        if (!teamService.isMember(teamId, user)) {
            throw new ForbiddenException("Только участник команды может играть командой");
        }
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Игра не найдена"));
        Team team = teamService.findTeamEntity(teamId);
        TeamGameSession session = sessionRepository.findTopByTeamIdAndGameIdOrderByCreatedAtDesc(teamId, gameId)
                .filter(s -> "LOBBY".equals(s.getStatus()))
                .orElse(null);
        if (session == null) {
            session = new TeamGameSession();
            session.setTeam(team);
            session.setGame(game);
            session.setStatus("LOBBY");
            session.setCurrentLevelIndex(0);
            session.setCreatedById(user.getId());
            sessionRepository.save(session);
        }
        markOnline(session, user);
        List<TeamMemberResponse> members = teamService.getMembers(team);
        return TeamGameLobbyResponse.from(session, members, onlineIds(session.getId()));
    }

    @Transactional
    public TeamGameLobbyResponse getLobby(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        return TeamGameLobbyResponse.from(session, teamService.getMembers(session.getTeam()), onlineIds(sessionId));
    }

    @Transactional
    public TeamGameStateResponse startGame(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        session.setStatus("IN_PROGRESS");
        session.setStartTime(Instant.now());
        session.setCurrentLevelIndex(0);
        sessionRepository.save(session);
        ensureProgress(session);
        addEvent(session, "Игра началась. Удачи, команда!");
        TeamGameStateResponse state = buildState(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STARTED");
        payload.put("state", state);
        broadcast(sessionId, payload);
        return state;
    }

    @Transactional
    public TeamGameStateResponse submitAnswer(Long sessionId, User user, TeamGameAnswerRequest request) {
        TeamGameSession session = requireSession(sessionId, user);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BadRequestException("Игра не запущена");
        }
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ANSWER_RESULT");
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("correct", correct);
        payload.put("answer", request.answer());
        payload.put("questionId", question.getId());

        if (correct) {
            TeamSessionProgress progress = ensureProgressForLevel(session, level);
            Map<String, String> solved = parseSolved(progress);
            String key = "l" + level.getId() + ":q" + question.getId() + ":a" + matched.getId();
            solved.put(key, matched.getAnswerText());
            progress.setSolvedData(writeSolved(solved));
            progressRepository.save(progress);
            addEvent(session, "Игрок " + user.getUsername() + " разгадал слово \"" + matched.getAnswerText() + "\"");
            boolean levelComplete = isLevelComplete(level, solved);
            payload.put("levelCompleted", levelComplete);
            if (levelComplete) {
                addEvent(session, "Команда прошла уровень \"" + level.getTitle() + "\"");
                advance(session);
            }
        } else {
            addEvent(session, "Игрок " + user.getUsername() + " ввёл неверный ответ");
        }
        payload.put("state", buildState(session));
        broadcast(sessionId, payload);
        return buildState(session);
    }

    @Transactional
    public TeamGameStateResponse requestHint(Long sessionId, User user, TeamGameHintRequest request) {
        TeamGameSession session = requireSession(sessionId, user);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new BadRequestException("Игра не запущена");
        }
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
        addEvent(session, "Команда получила подсказку (+" + Math.round((hint.getPenaltyTime() == null ? 0.0 : hint.getPenaltyTime()) * 60) + " сек. штрафа)");

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "HINT_USED");
        payload.put("hintIndex", request.hintIndex());
        payload.put("text", hint.getText());
        payload.put("imageUrl", hint.getImageUrl());
        payload.put("username", user.getUsername());
        payload.put("state", buildState(session));
        broadcast(sessionId, payload);
        return buildState(session);
    }

    @Transactional
    public TeamGameStateResponse getState(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        return buildState(session);
    }

    @Transactional
    public TeamGameStateResponse join(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        markOnline(session, user);
        addEvent(session, "Игрок " + user.getUsername() + " присоединился");
        TeamGameStateResponse state = buildState(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PRESENCE");
        payload.put("state", state);
        broadcast(sessionId, payload);
        return state;
    }

    @Transactional
    public void leave(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        presenceRepository.deleteBySessionIdAndUserId(sessionId, user.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PRESENCE");
        payload.put("state", buildState(session));
        broadcast(sessionId, payload);
    }

    @Transactional
    public void heartbeat(Long sessionId, User user) {
        TeamGameSession session = requireSession(sessionId, user);
        markOnline(session, user);
    }

    private void advance(TeamGameSession session) {
        List<GameLevel> levels = sortedLevels(session.getGame());
        if (session.getCurrentLevelIndex() >= levels.size() - 1) {
            session.setStatus("FINISHED");
            addEvent(session, "Команда завершила игру! 🏆");
        } else {
            session.setCurrentLevelIndex(session.getCurrentLevelIndex() + 1);
            addEvent(session, "Переход на уровень " + (session.getCurrentLevelIndex() + 1));
        }
        sessionRepository.save(session);
    }

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

    private TeamGameSession requireSession(Long sessionId, User user) {
        TeamGameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        if (!teamService.isMember(session.getTeam().getId(), user)) {
            throw new ForbiddenException("Доступ запрещён");
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

    private TeamGameStateResponse buildState(TeamGameSession session) {
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
                session.getStartTime(),
                eventList,
                onlineIds(session.getId()),
                memberNames
        );
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
    private void markOnline(TeamGameSession session, User user) {
        TeamSessionPresence presence = presenceRepository.findBySessionIdAndUserId(session.getId(), user.getId())
                .orElse(null);
        if (presence == null) {
            presence = new TeamSessionPresence();
            presence.setSession(session);
            presence.setUserId(user.getId());
        }
        presence.setUsername(user.getUsername());
        presence.setLastSeenAt(Instant.now());
        presenceRepository.save(presence);
    }

    /** Пользователи, чья последняя активность не старше окна присутствия. */
    private List<String> onlineIds(Long sessionId) {
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
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.forLanguageTag("ru"));
    }

    private void addEvent(TeamGameSession session, String text) {
        TeamSessionEvent event = new TeamSessionEvent();
        event.setSession(session);
        event.setText(text);
        event.setCreatedAt(Instant.now());
        eventRepository.save(event);
    }
}
