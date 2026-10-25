package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.ProfileUpdateRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.dto.UserSummaryResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
public class UserService {

    private static final int SEARCH_LIMIT = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    public UserService(UserRepository userRepository, ImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
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
        user.setAvatarUrl(imageStorageService.store(file));
        userRepository.save(user);
        return UserResponse.from(user);
    }

    /** Поиск зарегистрированных пользователей для приглашения в команду. */
    public List<UserSummaryResponse> searchUsers(String search, Long currentUserId) {
        String query = search == null ? "" : search.trim();
        if (query.length() > MAX_SEARCH_LENGTH) {
            query = query.substring(0, MAX_SEARCH_LENGTH);
        }
        return userRepository.search(query, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .filter(candidate -> currentUserId == null || !candidate.getId().equals(currentUserId))
                .map(UserSummaryResponse::from)
                .toList();
    }

    public Path resolveFile(String filename) {
        return imageStorageService.resolve(filename);
    }
}
