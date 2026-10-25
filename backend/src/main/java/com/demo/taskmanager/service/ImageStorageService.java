package com.demo.taskmanager.service;

import com.demo.taskmanager.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Общее хранилище картинок: аватары пользователей и команд. */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path uploadDir;

    public ImageStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    /** Сохранить картинку и вернуть публичный URL вида /api/files/{uuid}.{ext}. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл не выбран или пуст");
        }

        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (extension.length() > 5 || !extension.matches("[a-z0-9]+")) {
            extension = "png";
        }
        String type = file.getContentType();
        boolean typeAllowed = type != null && ALLOWED_TYPES.contains(type);
        boolean extAllowed = ALLOWED_EXTENSIONS.contains(extension);
        if (!typeAllowed && !extAllowed) {
            throw new BadRequestException("Поддерживаются только изображения (PNG, JPEG, WEBP, GIF)");
        }

        String filename = UUID.randomUUID() + "." + extension;
        try {
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Не удалось сохранить файл: " + e.getMessage());
        }
        return "/api/files/" + filename;
    }

    /** Безопасно найти загруженный файл по имени (без выхода за пределы каталога). */
    public Path resolve(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Некорректное имя файла");
        }
        Path path = uploadDir.resolve(filename).normalize();
        if (!path.startsWith(uploadDir) || !Files.exists(path)) {
            throw new BadRequestException("Файл не найден");
        }
        return path;
    }
}
