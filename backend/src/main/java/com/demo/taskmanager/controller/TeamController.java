package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.AvatarResponse;
import com.demo.taskmanager.dto.InvitationResponse;
import com.demo.taskmanager.dto.InviteRequest;
import com.demo.taskmanager.dto.TeamDetailsResponse;
import com.demo.taskmanager.dto.TeamRequest;
import com.demo.taskmanager.dto.TeamResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.service.ImageStorageService;
import com.demo.taskmanager.service.TeamService;
import com.demo.taskmanager.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Команды: создание, общий рейтинг, приглашения и управление участниками. */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final UserService userService;
    private final ImageStorageService imageStorageService;

    public TeamController(TeamService teamService, UserService userService,
                          ImageStorageService imageStorageService) {
        this.teamService = teamService;
        this.userService = userService;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping
    public List<TeamResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return teamService.listTeams(currentUser(principal));
    }

    @GetMapping("/{id}")
    public TeamDetailsResponse details(@AuthenticationPrincipal UserDetails principal,
                                       @PathVariable Long id) {
        return teamService.getTeam(id, currentUser(principal));
    }

    @PutMapping("/{id}")
    public TeamDetailsResponse update(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable Long id,
                                      @RequestBody TeamRequest request) {
        return teamService.updateTeam(id, request, currentUser(principal));
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@AuthenticationPrincipal UserDetails principal,
                                               @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.createTeam(request, currentUser(principal)));
    }

    /** Загрузка аватара команды до создания: возвращает URL для поля avatarUrl. */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse uploadAvatar(@RequestParam("file") MultipartFile file) {
        return new AvatarResponse(imageStorageService.store(file));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<InvitationResponse> invite(@AuthenticationPrincipal UserDetails principal,
                                                     @PathVariable Long id,
                                                     @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.invite(id, currentUser(principal), request));
    }

    /** Удалить участника может только создатель команды. */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable Long id,
                                             @PathVariable Long userId) {
        teamService.removeMember(id, userId, currentUser(principal));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable Long id) {
        teamService.leaveTeam(id, currentUser(principal));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
