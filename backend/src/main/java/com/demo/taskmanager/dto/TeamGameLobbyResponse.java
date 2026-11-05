package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Game;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;

import java.time.Instant;
import java.util.List;

/**
 * Лобби командной игры: команда, игра и (если партия уже идёт) сама сессия.
 *
 * Если sessionId задан — партия уже запущена, игрок может присоединиться.
 */
public record TeamGameLobbyResponse(
        Long sessionId,
        Long teamId,
        String teamName,
        String teamAvatarUrl,
        Long gameId,
        String gameTitle,
        String status,
        int currentLevelIndex,
        Instant startTime,
        Instant createdAt,
        Long hostId,
        String hostUsername,
        List<TeamMemberResponse> members,
        List<String> onlineUserIds
) {
    public static TeamGameLobbyResponse from(Team team, Game game, TeamGameSession session,
                                             List<TeamMemberResponse> members,
                                             List<String> onlineUserIds) {
        return new TeamGameLobbyResponse(
                session == null ? null : session.getId(),
                team.getId(),
                team.getName(),
                team.getAvatarUrl(),
                game.getId(),
                game.getTitle(),
                session == null ? null : session.getStatus(),
                session == null ? 0 : session.getCurrentLevelIndex(),
                session == null ? null : session.getStartTime(),
                session == null ? null : session.getCreatedAt(),
                session == null ? null : session.getHostId(),
                session == null ? null : session.getHostUsername(),
                members,
                onlineUserIds
        );
    }
}
