package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.ProfileUpdateRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.repository.UserRepository;
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

@Service
public class UserService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final UserRepository userRepository;
    private final Path uploadDir;

    public UserService(UserRepository userRepository,
                       @Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.userRepository = userRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Пользователь не найден"));
    }

    public UserResponse getProfile(User user) {
        return UserResponse.from(user);
    }

    public UserResponse updateProfile(User user, ProfileUpdateRequest request) {
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim());
        }
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public UserResponse updateAvatar(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Файл аватара не передан");
        }

        String original = file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename();
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (extension.length() > 5 || !extension.matches("[a-z0-9]+")) {
            extension = "png";
        }
        String type = file.getContentType();
        boolean typeAllowed = type != null && ALLOWED_TYPES.contains(type);
        boolean extAllowed = Set.of("png", "jpg", "jpeg", "webp", "gif").contains(extension);
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

        user.setAvatarUrl("/api/files/" + filename);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public Path resolveFile(String filename) {
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
