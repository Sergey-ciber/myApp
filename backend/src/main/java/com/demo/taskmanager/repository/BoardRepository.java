package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Board;
import com.demo.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findByOwnerOrderByCreatedAtDesc(User owner);
    Optional<Board> findByIdAndOwner(Long id, User owner);

    /** Передать все доски удаляемого пользователя другому владельцу. */
    @Modifying
    @Query("update Board b set b.owner = :newOwner where b.owner.id = :userId")
    int reassignOwner(@Param("userId") Long userId, @Param("newOwner") User newOwner);
}
