package com.demo.taskmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Укажите логин") @Size(min = 3, max = 50) String username,
        @NotBlank(message = "Укажите пароль") @Size(min = 6, max = 100) String password,
        @NotBlank(message = "Укажите email")
        @Email(message = "Некорректный email")
        @Size(max = 100, message = "Email слишком длинный")
        String email
) {}
