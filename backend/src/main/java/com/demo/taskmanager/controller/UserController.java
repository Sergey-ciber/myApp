package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.ProfileUpdateRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByUsername(principal.getUsername());
        return userService.getProfile(user);
    }

    @PutMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal UserDetails principal,
                                      @RequestBody ProfileUpdateRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return userService.updateProfile(user, request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal UserDetails principal,
                                     @RequestParam("file") MultipartFile file) {
        User user = userService.getByUsername(principal.getUsername());
        return userService.updateAvatar(user, file);
    }
}
