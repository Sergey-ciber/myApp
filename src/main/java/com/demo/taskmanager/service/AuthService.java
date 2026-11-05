package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.AuthResponse;
import com.demo.taskmanager.dto.LoginRequest;
import com.demo.taskmanager.dto.RegisterRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.repository.UserRepository;
import com.demo.taskmanager.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        // Логины нечувствительны к регистру, поэтому «Иван» и «иван» — один и тот же логин.
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Пользователь с таким логином уже существует");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(username);
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, UserResponse.from(user));
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        // Логин нечувствителен к регистру.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.password()));

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadRequestException("Пользователь не найден"));

        String token = jwtService.generateToken(user.getUsername());
        return new AuthResponse(token, UserResponse.from(user));
    }
}
