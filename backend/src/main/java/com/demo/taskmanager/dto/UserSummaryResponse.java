package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.User;

/** Безопасная карточка пользователя для поиска и списков участников. */
public record UserSummaryResponse(Long id, String username, String fullName, String avatarUrl) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getUsername(),
                user.getFullName(), user.getAvatarUrl());
    }
}
