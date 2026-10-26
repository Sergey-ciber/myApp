package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamMember;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.time.Instant;
import java.util.List;

/** Команда в списке: рейтинг, участники и права текущего пользователя. */
public record TeamResponse(Long id, String name, String avatarUrl,
                           Long creatorId, String creatorUsername,
                           int wins, int memberCount, Instant createdAt,
                           List<TeamMemberResponse> members,
                           boolean currentUserCreator, boolean currentUserMember,
                           String currentUserRole) {

    public static TeamResponse from(Team team, Long currentUserId) {
        List<TeamMemberResponse> members = team.getMembers().stream()
                .map(TeamMemberResponse::from)
                .toList();
        TeamMember own = team.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(currentUserId))
                .findFirst()
                .orElse(null);
        boolean creator = team.getCreator() != null
                && team.getCreator().getId().equals(currentUserId);
        return new TeamResponse(team.getId(), team.getName(), team.getAvatarUrl(),
                team.getCreator().getId(), team.getCreator().getUsername(),
                team.getWins(), members.size(), team.getCreatedAt(), members,
                creator, own != null, own == null ? null : own.getRole().name());
    }

    // Совместимое snake_case-поле для внешних клиентов; основной формат API — camelCase.
    @JsonGetter("creator_id")
    public Long creatorIdSnakeCase() { return creatorId; }
}
