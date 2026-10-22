package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.Board;
import com.demo.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findByOwnerOrderByCreatedAtDesc(User owner);
    Optional<Board> findByIdAndOwner(Long id, User owner);
}
