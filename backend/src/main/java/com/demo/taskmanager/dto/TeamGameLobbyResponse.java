package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.TeamGameSession;

import java.time.Instant;
import java.util.List;

public record TeamGameLobbyResponse(
        Long sessionId,
        Long teamId,
        Long gameId,
        String gameTitle,
        String status,
        int currentLevelIndex,
        Instant startTime,
        Instant createdAt,
        List<TeamMemberResponse> members,
        List<String> onlineUserIds
) {
    public static TeamGameLobbyResponse from(TeamGameSession session, List<TeamMemberResponse> members, List<String> onlineUserIds) {
        return new TeamGameLobbyResponse(
                session.getId(),
                session.getTeam().getId(),
                session.getGame().getId(),
                session.getGame().getTitle(),
                session.getStatus(),
                session.getCurrentLevelIndex(),
                session.getStartTime(),
                session.getCreatedAt(),
                members,
                onlineUserIds
        );
    }
}
