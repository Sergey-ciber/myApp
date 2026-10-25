package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Все команды для общего списка: сначала больше побед, затем более ранние. */
    @EntityGraph(attributePaths = {"members", "members.user", "creator"})
    @Query("select distinct t from Team t order by t.wins desc, t.createdAt asc, t.id asc")
    List<Team> findAllWithDetails();

    /** Начислить +1 каждой команде, в которой состоит победитель. */
    @Modifying
    @Query("update Team t set t.wins = t.wins + 1 " +
            "where t.id in (select m.team.id from TeamMember m where m.user.id = :userId)")
    int incrementWinsForMember(@Param("userId") Long userId);
}
