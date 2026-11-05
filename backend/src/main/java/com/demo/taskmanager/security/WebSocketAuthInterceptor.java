package com.demo.taskmanager.security;

import com.demo.taskmanager.repository.TeamGameSessionRepository;
import com.demo.taskmanager.repository.TeamMemberRepository;
import com.demo.taskmanager.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аутентификация WebSocket-соединения и проверка прав на подписку.
 *
 * Клиент передаёт JWT в заголовке CONNECT (нативный заголовок SockJS).
 * Без этого любой посетитель мог подписаться на чужой канал
 * /topic/user_invitations_{id} и увидеть чужие приглашения.
 *
 * id пользователя сохраняется в атрибутах STOMP-сессии: SecurityContext
 * не переносится в поток обработки сообщений, поэтому проверять права
 * нужно по атрибутам сессии, а не по SecurityContextHolder.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /** Канал приглашений конкретного пользователя: /topic/user_invitations_{userId}. */
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/user_invitations_(\\d+)$");

    /** Канал команды: /topic/team_{teamId} — доступен только участникам команды. */
    private static final Pattern TEAM_TOPIC = Pattern.compile("^/topic/team_(\\d+)$");

    /** Канал комнаты командной игры: /topic/team_session_{sessionId} — только участники сессии. */
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/team_session_(\\d+)$");

    /** Имя атрибута сессии, где хранится id авторизованного пользователя. */
    private static final String SESSION_USER_ID = "wsUserId";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamGameSessionRepository sessionRepository;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                     UserRepository userRepository,
                                     TeamMemberRepository teamMemberRepository,
                                     TeamGameSessionRepository sessionRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (command == StompCommand.SUBSCRIBE || command == StompCommand.UNSUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination == null) {
                return message;
            }
            Long currentUserId = sessionUserId(accessor);

            Matcher userMatcher = USER_TOPIC.matcher(destination);
            if (userMatcher.matches()) {
                Long topicUserId = Long.valueOf(userMatcher.group(1));
                if (currentUserId == null || !currentUserId.equals(topicUserId)) {
                    // подписка на чужой канал запрещена — отклоняем кадр
                    throw new org.springframework.messaging.MessagingException(
                            "Нет доступа к каналу " + destination);
                }
                return message;
            }

            Matcher teamMatcher = TEAM_TOPIC.matcher(destination);
            if (teamMatcher.matches()) {
                Long teamId = Long.valueOf(teamMatcher.group(1));
                if (currentUserId == null || !teamMemberRepository.existsByTeamIdAndUserId(teamId, currentUserId)) {
                    throw new org.springframework.messaging.MessagingException(
                            "Нет доступа к каналу " + destination);
                }
                return message;
            }

            // Комната командной игры: пускаем только участников команды этой сессии.
            Matcher sessionMatcher = SESSION_TOPIC.matcher(destination);
            if (sessionMatcher.matches()) {
                Long sessionId = Long.valueOf(sessionMatcher.group(1));
                Long teamId = sessionRepository.findById(sessionId)
                        .map(session -> session.getTeam() == null ? null : session.getTeam().getId())
                        .orElse(null);
                if (teamId == null || currentUserId == null
                        || !teamMemberRepository.existsByTeamIdAndUserId(teamId, currentUserId)) {
                    throw new org.springframework.messaging.MessagingException(
                            "Нет доступа к каналу " + destination);
                }
                return message;
            }
            // остальные каналы доступны как раньше
        }
        return message;
    }

    /** Достаём JWT из заголовков CONNECT и сохраняем id пользователя в сессии. */
    private void authenticate(StompHeaderAccessor accessor) {
        String token = readToken(accessor);
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            String username = jwtService.extractUsername(token);
            if (username == null) {
                return;
            }
            Long userId = userRepository.findByUsernameIgnoreCase(username)
                    .map(user -> user.getId())
                    .orElse(null);
            if (userId == null) {
                return;
            }
            accessor.getSessionAttributes().put(SESSION_USER_ID, userId);
        } catch (Exception ignored) {
            // невалидный токен — сессия остаётся без пользователя,
            // подписка на персональные каналы будет отклонена
        }
    }

    private String readToken(StompHeaderAccessor accessor) {
        for (String header : List.of("Authorization", "authorization")) {
            List<String> values = accessor.getNativeHeader(header);
            if (values != null && !values.isEmpty()) {
                String value = values.get(0);
                if (value != null && value.startsWith("Bearer ")) {
                    return value.substring(7);
                }
                return value;
            }
        }
        return null;
    }

    /** id пользователя, установленного при CONNECT. */
    private Long sessionUserId(StompHeaderAccessor accessor) {
        var attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object userId = attributes.get(SESSION_USER_ID);
        return userId instanceof Long value ? value : null;
    }
}
