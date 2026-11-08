package com.demo.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Отправка письма со ссылкой сброса пароля.
 *
 * Ссылка и токен никогда не пишутся в логи. Если SMTP не настроен,
 * письмо не отправляется, а в лог попадает только предупреждение.
 */
@Service
public class PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String from;
    private final String baseUrl;

    public PasswordResetMailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    @Value("${spring.mail.host:}") String mailHost,
                                    @Value("${app.password-reset.from:no-reply@localhost}") String from,
                                    @Value("${app.password-reset.base-url:}") String baseUrl,
                                    @Value("${app.cors.allowed-origins:}") String corsOrigins) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.from = from;
        this.baseUrl = resolveBaseUrl(baseUrl, corsOrigins);
    }

    /**
     * Публичный адрес приложения для ссылки в письме.
     *
     * APP_BASE_URL может быть не задан или пуст (например, пустая переменная
     * из docker compose) — тогда берём первый адрес из CORS_ORIGINS. Список
     * origins через запятую в ссылку попасть не должен, поэтому используем
     * только первый элемент.
     */
    private static String resolveBaseUrl(String baseUrl, String corsOrigins) {
        String resolved = firstNonBlank(baseUrl, corsOrigins, "http://localhost:5173");
        return resolved.replaceAll("/+$", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "http://localhost:5173";
    }

    @Async("mailTaskExecutor")
    public void sendPasswordReset(String email, String token, Instant expiresAt) {
        if (mailHost == null || mailHost.isBlank()) {
            log.warn("Сброс пароля: SMTP не настроен (spring.mail.host), письмо не отправлено");
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Сброс пароля: JavaMailSender недоступен, письмо не отправлено");
            return;
        }

        String link = baseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Сброс пароля");
        message.setText("""
                Здравствуйте!

                Вы запросили сброс пароля.
                Чтобы задать новый пароль, перейдите по ссылке:
                %s

                Ссылка действует до %s и может быть использована только один раз.
                Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо.
                """.formatted(link, EXPIRY_FORMAT.format(expiresAt)));

        try {
            mailSender.send(message);
            log.info("Письмо для сброса пароля отправлено");
        } catch (MailException e) {
            // Не логируем ссылку и токен — только причину сбоя.
            log.warn("Не удалось отправить письмо для сброса пароля: {}", e.getMessage());
        }
    }
}
