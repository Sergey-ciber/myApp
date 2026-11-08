package com.demo.taskmanager.config;

import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.entity.UserRole;
import com.demo.taskmanager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Идемпотентно создаёт супер-администратора при старте приложения.
 *
 * <p>Логин и пароль задаются в конфигурации ({@code superadmin.username} и
 * {@code superadmin.password}). В БД хранится только BCrypt-хэш пароля.
 * Если логин уже занят обычным пользователем, он повышается до SUPERADMIN,
 * а пароль синхронизируется с конфигурацией.</p>
 */
@Component
public class SuperAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredUsername;
    private final String configuredPassword;

    public SuperAdminInitializer(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${superadmin.username:superadmin}") String configuredUsername,
                                 @Value("${superadmin.password:serg6363}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(String... args) {
        String username = configuredUsername == null ? "" : configuredUsername.trim();
        if (username.isEmpty() || configuredPassword == null || configuredPassword.isEmpty()) {
            log.warn("Супер-администратор не создан: задайте superadmin.username и superadmin.password");
            return;
        }

        Optional<User> existing = userRepository.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            ensureSuperAdmin(existing.get());
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setFullName(username);
        admin.setPassword(passwordEncoder.encode(configuredPassword));
        admin.setRole(UserRole.SUPERADMIN);
        try {
            userRepository.save(admin);
            log.info("Создан супер-администратор «{}»", username);
        } catch (DataIntegrityViolationException e) {
            // Параллельный старт другого экземпляра уже создал пользователя.
            log.warn("Супер-администратор «{}» уже создан другим экземпляром", username);
        }
    }

    private void ensureSuperAdmin(User user) {
        boolean changed = false;
        if (!user.isSuperAdmin()) {
            user.setRole(UserRole.SUPERADMIN);
            changed = true;
            log.info("Пользователь «{}» повышен до супер-администратора", user.getUsername());
        }
        // Пароль всегда соответствует конфигурации: это «жёстко заданные» учётные данные.
        if (!passwordEncoder.matches(configuredPassword, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(configuredPassword));
            changed = true;
            log.info("Пароль супер-администратора «{}» синхронизирован с конфигурацией",
                    user.getUsername());
        }
        if (changed) {
            userRepository.save(user);
        }
    }
}
