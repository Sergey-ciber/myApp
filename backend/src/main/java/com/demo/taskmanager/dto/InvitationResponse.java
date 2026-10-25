package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.TeamInvitation;

import java.time.Instant;
import java.util.Locale;

public record InvitationResponse(Long id, Long teamId, String teamName, String teamAvatarUrl,
                                 Long inviterId, String inviterUsername, String status,
                                 Instant createdAt) {

    public static InvitationResponse from(TeamInvitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getTeam().getId(),
                invitation.getTeam().getName(), invitation.getTeam().getAvatarUrl(),
                invitation.getInviter().getId(), invitation.getInviter().getUsername(),
                invitation.getStatus().name().toLowerCase(Locale.ROOT), invitation.getCreatedAt());
    }
}
