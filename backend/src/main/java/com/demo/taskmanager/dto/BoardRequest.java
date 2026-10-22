package com.demo.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        String color
) {}
