package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Поиск по логину и полному имени для приглашения в команду. */
    @Query("select u from User u where :query = '' " +
            "or lower(u.username) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(u.fullName, '')) like lower(concat('%', :query, '%')) " +
            "order by u.username asc")
    List<User> search(@Param("query") String query, Pageable pageable);
}
