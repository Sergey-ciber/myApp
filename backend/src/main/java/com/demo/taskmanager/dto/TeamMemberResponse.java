package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.TeamMember;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.time.Instant;

public record TeamMemberResponse(Long userId, String username, String fullName,
                                 String avatarUrl, String role, Instant joinedAt) {

    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(member.getUser().getId(), member.getUser().getUsername(),
                member.getUser().getFullName(), member.getUser().getAvatarUrl(),
                member.getRole().name(), member.getJoinedAt());
    }

    // Совместимые snake_case-поля для внешних клиентов; основной формат API — camelCase.
    @JsonGetter("user_id")
    public Long userIdSnakeCase() { return userId; }

    @JsonGetter("avatar_url")
    public String avatarUrlSnakeCase() { return avatarUrl; }

    @JsonGetter("joined_at")
    public Instant joinedAtSnakeCase() { return joinedAt; }
}
