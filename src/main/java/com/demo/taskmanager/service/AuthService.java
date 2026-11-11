package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.AuthResponse;
import com.demo.taskmanager.dto.ForgotPasswordRequest;
import com.demo.taskmanager.dto.LoginRequest;
import com.demo.taskmanager.dto.RegisterRequest;
import com.demo.taskmanager.dto.ResetPasswordRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.TooManyRequestsException;
import com.demo.taskmanager.repository.UserRepository;
import com.demo.taskmanager.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetMailService passwordResetMailService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final Duration resetTokenTtl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager,
                       PasswordResetMailService passwordResetMailService,
                       PasswordResetRateLimiter passwordResetRateLimiter,
                       @Value("${app.password-reset.token-ttl-minutes:60}") long resetTokenTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.passwordResetMailService = passwordResetMailService;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.resetTokenTtl = Duration.ofMinutes(Math.max(1, resetTokenTtlMinutes));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        // Логины нечувствительны к регистру, поэтому «Иван» и «иван» — один и тот же логин.
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Пользователь с таким логином уже существует");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Пользователь с таким email уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(email);
        user.setFullName(username);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Гонка двух одновременных регистраций: сработал уникальный индекс.
            throw new BadRequestException("Пользователь с таким логином или email уже существует");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, UserResponse.from(user));
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        // Логин нечувствителен к регистру.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.password()));

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadRequestException("Пользователь не найден"));

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());
        return new AuthResponse(token, UserResponse.from(user));
    }

    /**
     * Запрос на восстановление пароля.
     *
     * Всегда завершается успешно для существующего и несуществующего email,
     * чтобы нельзя было определить, зарегистрирован ли пользователь.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request, String clientIp) {
        String email = normalizeEmail(request.email());

        if (!passwordResetRateLimiter.tryAcquire("forgot:ip:" + clientIp)
                || !passwordResetRateLimiter.tryAcquire("forgot:email:" + email)) {
            throw new TooManyRequestsException("Слишком много запросов. Попробуйте позже");
        }

        userRepository.findFirstByEmailIgnoreCase(email).ifPresent(user -> {
            String rawToken = generateResetToken();
            Instant expiresAt = Instant.now().plus(resetTokenTtl);
            user.setResetToken(hashToken(rawToken));
            user.setResetTokenExpiry(expiresAt);
            userRepository.save(user);

            // В письмо уходит исходный токен, в БД хранится только его хэш.
            // Сбой почты не должен раскрывать существование аккаунта и ронять запрос.
            try {
                passwordResetMailService.sendPasswordReset(user.getEmail(), rawToken, expiresAt);
            } catch (RuntimeException e) {
                log.warn("Не удалось отправить письмо для сброса пароля: {}", e.getMessage());
            }
        });
    }

    /** Сброс пароля по одноразовому токену из письма. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        if (!passwordResetRateLimiter.tryAcquire("reset:ip:" + clientIp)) {
            throw new TooManyRequestsException("Слишком много запросов. Попробуйте позже");
        }

        String tokenHash = hashToken(request.token().trim());
        User user = userRepository.findByResetTokenForUpdate(tokenHash)
                .orElseThrow(() -> new BadRequestException("Ссылка недействительна или истекла"));

        if (user.getResetTokenExpiry() == null || !user.getResetTokenExpiry().isAfter(Instant.now())) {
            throw new BadRequestException("Ссылка недействительна или истекла");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Токен одноразовый: после успешного сброса он больше не действует.
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Криптографически стойкий URL-безопасный токен (256 бит). */
    private static String generateResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /** В БД храним только SHA-256 хэш токена. */
    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
