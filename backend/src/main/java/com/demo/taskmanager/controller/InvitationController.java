package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.InvitationResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.service.TeamService;
import com.demo.taskmanager.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Входящие приглашения в команды для текущего пользователя. */
@RestController
@RequestMapping("/api")
public class InvitationController {

    private final TeamService teamService;
    private final UserService userService;

    public InvitationController(TeamService teamService, UserService userService) {
        this.teamService = teamService;
        this.userService = userService;
    }

    @GetMapping("/users/me/invitations")
    public List<InvitationResponse> incoming(@AuthenticationPrincipal UserDetails principal) {
        return teamService.incomingInvitations(currentUser(principal));
    }

    @PostMapping("/invitations/{id}/accept")
    public InvitationResponse accept(@AuthenticationPrincipal UserDetails principal,
                                     @PathVariable Long id) {
        return teamService.acceptInvitation(id, currentUser(principal));
    }

    @PostMapping("/invitations/{id}/decline")
    public InvitationResponse decline(@AuthenticationPrincipal UserDetails principal,
                                      @PathVariable Long id) {
        return teamService.declineInvitation(id, currentUser(principal));
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
