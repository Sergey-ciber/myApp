package com.demo.taskmanager.dto;

import com.demo.taskmanager.entity.User;

public record UserResponse(Long id, String username, String fullName, String email, String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.getAvatarUrl());
    }
}
