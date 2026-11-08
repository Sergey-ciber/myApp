package com.demo.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Токен не указан") String token,
        @NotBlank(message = "Укажите новый пароль")
        @Size(min = 6, max = 100, message = "Пароль должен содержать от 6 до 100 символов")
        String newPassword
) {}
