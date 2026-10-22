package com.demo.taskmanager.controller;

import com.demo.taskmanager.dto.BoardRequest;
import com.demo.taskmanager.dto.BoardResponse;
import com.demo.taskmanager.dto.TaskRequest;
import com.demo.taskmanager.dto.TaskResponse;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.service.BoardService;
import com.demo.taskmanager.service.TaskService;
import com.demo.taskmanager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;
    private final TaskService taskService;
    private final UserService userService;

    public BoardController(BoardService boardService, TaskService taskService, UserService userService) {
        this.boardService = boardService;
        this.taskService = taskService;
        this.userService = userService;
    }

    @GetMapping
    public List<BoardResponse> list(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByUsername(principal.getUsername());
        return boardService.listBoards(user);
    }

    @PostMapping
    public ResponseEntity<BoardResponse> create(@AuthenticationPrincipal UserDetails principal,
                                                @Valid @RequestBody BoardRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createBoard(user, request));
    }

    @GetMapping("/{id}")
    public BoardResponse get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        User user = userService.getByUsername(principal.getUsername());
        return boardService.getBoard(user, id);
    }

    @PutMapping("/{id}")
    public BoardResponse update(@AuthenticationPrincipal UserDetails principal,
                                @PathVariable Long id,
                                @Valid @RequestBody BoardRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return boardService.updateBoard(user, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        User user = userService.getByUsername(principal.getUsername());
        boardService.deleteBoard(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/tasks")
    public List<TaskResponse> listTasks(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        User user = userService.getByUsername(principal.getUsername());
        return taskService.listTasks(user, id);
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<TaskResponse> createTask(@AuthenticationPrincipal UserDetails principal,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody TaskRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(user, id, request));
    }

    @PutMapping("/tasks/{taskId}")
    public TaskResponse updateTask(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long taskId,
                                   @Valid @RequestBody TaskRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return taskService.updateTask(user, taskId, request);
    }

    @PatchMapping("/tasks/{taskId}/move")
    public TaskResponse moveTask(@AuthenticationPrincipal UserDetails principal,
                                 @PathVariable Long taskId,
                                 @RequestBody Map<String, String> body) {
        User user = userService.getByUsername(principal.getUsername());
        Integer position = body.containsKey("position") ? Integer.valueOf(body.get("position")) : 0;
        return taskService.moveTask(user, taskId,
                com.demo.taskmanager.entity.Task.Status.valueOf(body.get("status")), position);
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal UserDetails principal, @PathVariable Long taskId) {
        User user = userService.getByUsername(principal.getUsername());
        taskService.deleteTask(user, taskId);
        return ResponseEntity.noContent().build();
    }
}
