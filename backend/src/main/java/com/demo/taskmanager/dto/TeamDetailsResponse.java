package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamMember;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.time.Instant;
import java.util.List;

/** Детальная карточка команды: статистика, состав и права текущего пользователя. */
public record TeamDetailsResponse(
        Long id,
        String name,
        String avatarUrl,
        int wins,
        int membersCount,
        long gamesPlayed,
        Long creatorId,
        String creatorUsername,
        Instant createdAt,
        List<TeamMemberResponse> members,
        boolean currentUserCreator,
        boolean currentUserMember,
        String currentUserRole) {

    public static TeamDetailsResponse from(Team team, Long currentUserId, long gamesPlayed) {
        List<TeamMemberResponse> members = team.getMembers().stream()
                .map(TeamMemberResponse::from)
                .toList();
        TeamMember own = team.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(currentUserId))
                .findFirst()
                .orElse(null);
        boolean creator = team.getCreator() != null
                && team.getCreator().getId().equals(currentUserId);
        return new TeamDetailsResponse(
                team.getId(),
                team.getName(),
                team.getAvatarUrl(),
                team.getWins(),
                members.size(),
                gamesPlayed,
                team.getCreator().getId(),
                team.getCreator().getUsername(),
                team.getCreatedAt(),
                members,
                creator,
                own != null,
                own == null ? null : own.getRole().name());
    }

    // Совместимые snake_case-поля для внешних клиентов; основной формат API — camelCase.
    @JsonGetter("avatar_url")
    public String avatarUrlSnakeCase() { return avatarUrl; }

    @JsonGetter("members_count")
    public int membersCountSnakeCase() { return membersCount; }

    @JsonGetter("games_played")
    public long gamesPlayedSnakeCase() { return gamesPlayed; }

    @JsonGetter("creator_id")
    public Long creatorIdSnakeCase() { return creatorId; }
}
