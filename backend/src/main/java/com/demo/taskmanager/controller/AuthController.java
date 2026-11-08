package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.AuthResponse;
import com.demo.taskmanager.dto.ForgotPasswordRequest;
import com.demo.taskmanager.dto.LoginRequest;
import com.demo.taskmanager.dto.RegisterRequest;
import com.demo.taskmanager.dto.ResetPasswordRequest;
import com.demo.taskmanager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Запрос на сброс пароля. Всегда 200, даже если email не зарегистрирован —
     * это защищает от перебора пользователей.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        authService.requestPasswordReset(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                              HttpServletRequest httpRequest) {
        authService.resetPassword(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
