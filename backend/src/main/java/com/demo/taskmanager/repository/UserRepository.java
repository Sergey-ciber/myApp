package com.demo.taskmanager.repository;

import com.demo.taskmanager.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /** Поиск без учёта регистра — логин нечувствителен к регистру букв. */
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsername(String username);

    /** Проверка занятости логина без учёта регистра. */
    boolean existsByUsernameIgnoreCase(String username);

    /** Email нечувствителен к регистру. */
    Optional<User> findFirstByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Поиск по хэшу токена сброса с блокировкой строки: два одновременных
     * запроса не смогут использовать один и тот же токен дважды.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.resetToken = :token")
    Optional<User> findByResetTokenForUpdate(@Param("token") String token);

    /** Поиск по логину и полному имени для приглашения в команду. */
    @Query("select u from User u where :query = '' " +
            "or lower(u.username) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(u.fullName, '')) like lower(concat('%', :query, '%')) " +
            "order by u.username asc")
    List<User> search(@Param("query") String query, Pageable pageable);
}
