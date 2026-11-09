package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.InvitationStatus;
import com.demo.taskmanager.entity.TeamInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    /** Удалить приглашения, где пользователь был приглашающим или приглашённым. */
    @Modifying
    @Query("delete from TeamInvitation i where i.inviter.id = :userId or i.invitee.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"team", "inviter"})
    List<TeamInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, InvitationStatus status);

    Optional<TeamInvitation> findByIdAndInviteeId(Long id, Long inviteeId);

    boolean existsByTeamIdAndInviteeIdAndStatus(Long teamId, Long inviteeId, InvitationStatus status);

    @EntityGraph(attributePaths = {"team", "inviter"})
    List<TeamInvitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
