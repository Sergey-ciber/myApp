package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.TeamMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @EntityGraph(attributePaths = {"user", "team"})
    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    boolean existsByTeamIdInAndUserId(java.util.Collection<Long> teamIds, Long userId);

    @EntityGraph(attributePaths = {"user", "team"})
    List<TeamMember> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "team"})
    List<TeamMember> findByTeamIdIn(java.util.Collection<Long> teamIds);

    @EntityGraph(attributePaths = "user")
    List<TeamMember> findByTeamIdOrderByJoinedAtAscIdAsc(Long teamId);

    long countByTeamId(Long teamId);
}
