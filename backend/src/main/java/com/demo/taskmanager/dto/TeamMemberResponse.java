package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.TeamMember;

public record TeamMemberResponse(Long userId, String username, String fullName,
                                 String avatarUrl, String role) {

    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(member.getUser().getId(), member.getUser().getUsername(),
                member.getUser().getFullName(), member.getUser().getAvatarUrl(),
                member.getRole().name());
    }
}
