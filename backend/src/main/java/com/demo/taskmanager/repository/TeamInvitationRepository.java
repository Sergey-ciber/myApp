package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.InvitationStatus;
import com.demo.taskmanager.entity.TeamInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    @EntityGraph(attributePaths = {"team", "inviter"})
    List<TeamInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, InvitationStatus status);

    Optional<TeamInvitation> findByIdAndInviteeId(Long id, Long inviteeId);

    boolean existsByTeamIdAndInviteeIdAndStatus(Long teamId, Long inviteeId, InvitationStatus status);

    @EntityGraph(attributePaths = {"team", "inviter"})
    List<TeamInvitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
