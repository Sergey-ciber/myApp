package com.demo.taskmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Укажите email")
        @Email(message = "Некорректный email")
        @Size(max = 100, message = "Email слишком длинный")
        String email
) {}
