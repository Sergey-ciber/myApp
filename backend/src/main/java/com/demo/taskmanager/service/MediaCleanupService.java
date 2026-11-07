package com.demo.taskmanager.service;

import com.demo.taskmanager.repository.MediaReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Регулярная очистка неиспользуемых медиафайлов.
 *
 * Задача сверяет содержимое каталога загрузок со ссылками в БД и удаляет
 * файлы, на которые больше никто не ссылается. Файлы, загруженные недавно,
 * не трогаются: пользователь мог загрузить медиа, но ещё не сохранить игру.
 *
 * Если БД недоступна, очистка пропускается целиком — лучше оставить мусор,
 * чем удалить используемый файл.
 */
@Service
@ConditionalOnProperty(name = "app.media-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupService.class);
    private static final String FILES_URL_PREFIX = "/api/files/";

    private final ImageStorageService imageStorageService;
    private final MediaReferenceRepository mediaReferenceRepository;
    private final Duration minAge;

    public MediaCleanupService(ImageStorageService imageStorageService,
                               MediaReferenceRepository mediaReferenceRepository,
                               @Value("${app.media-cleanup.min-age-minutes:1440}") long minAgeMinutes) {
        this.imageStorageService = imageStorageService;
        this.mediaReferenceRepository = mediaReferenceRepository;
        this.minAge = Duration.ofMinutes(Math.max(0, minAgeMinutes));
    }

    @Scheduled(cron = "${app.media-cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        Path uploadDir = imageStorageService.getUploadDir();

        Set<String> referenced;
        try {
            referenced = referencedFileNames(mediaReferenceRepository.findReferencedUrls());
        } catch (Exception e) {
            log.warn("Очистка медиа пропущена: не удалось получить ссылки из БД", e);
            return;
        }

        Instant cutoff = Instant.now().minus(minAge);
        int scanned = 0;
        int deleted = 0;
        int kept = 0;

        try (Stream<Path> entries = Files.list(uploadDir)) {
            List<Path> files = entries.toList();
            for (Path file : files) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                if (name.startsWith(".")) continue; // служебные файлы вроде .gitkeep

                scanned++;
                if (referenced.contains(name)) continue;

                try {
                    FileTime modified = Files.getLastModifiedTime(file);
                    if (modified.toInstant().isAfter(cutoff)) {
                        // Загружен недавно: возможно, форма ещё не сохранена.
                        kept++;
                        continue;
                    }
                    if (imageStorageService.delete(name)) {
                        deleted++;
                        log.info("Удалён неиспользуемый медиафайл: {}", name);
                    }
                } catch (IOException e) {
                    log.warn("Не удалось проверить/удалить медиафайл {}: {}", name, e.getMessage());
                } catch (RuntimeException e) {
                    log.warn("Не удалось удалить медиафайл {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Не удалось прочитать каталог загрузок {}", uploadDir, e);
            return;
        }

        if (deleted > 0) {
            log.info("Очистка медиа: просмотрено {}, удалено {}, оставлено новых {}",
                    scanned, deleted, kept);
        } else {
            log.debug("Очистка медиа: просмотрено {}, удалено {}, оставлено новых {}",
                    scanned, deleted, kept);
        }
    }

    /** Имена файлов из upload-dir, на которые есть ссылки в БД. */
    static Set<String> referencedFileNames(Set<String> urls) {
        Set<String> names = new HashSet<>();
        if (urls == null) return names;
        for (String url : urls) {
            String name = fileNameOf(url);
            if (name != null) names.add(name);
        }
        return names;
    }

    /**
     * Достаёт имя файла из URL вида /api/files/{name} (в том числе абсолютного).
     * Для внешних ссылок и небезопасных значений возвращает null.
     */
    static String fileNameOf(String url) {
        if (url == null) return null;
        int index = url.indexOf(FILES_URL_PREFIX);
        if (index < 0) return null;

        String name = url.substring(index + FILES_URL_PREFIX.length());
        int cut = name.length();
        int query = name.indexOf('?');
        if (query >= 0) cut = Math.min(cut, query);
        int hash = name.indexOf('#');
        if (hash >= 0) cut = Math.min(cut, hash);
        name = name.substring(0, cut).trim();

        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        return name;
    }
}
