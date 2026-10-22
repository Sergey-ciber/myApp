package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.TaskRequest;
import com.demo.taskmanager.dto.TaskResponse;
import com.demo.taskmanager.entity.Board;
import com.demo.taskmanager.entity.Task;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardService boardService;

    public TaskService(TaskRepository taskRepository, BoardService boardService) {
        this.taskRepository = taskRepository;
        this.boardService = boardService;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(User user, Long boardId) {
        Board board = boardService.findOwned(user, boardId);
        return taskRepository.findByBoardOrderByPositionAsc(board).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse createTask(User user, Long boardId, TaskRequest request) {
        Board board = boardService.findOwned(user, boardId);

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStatus(parseStatus(request.status(), Task.Status.TODO));
        task.setDueDate(request.dueDate());
        task.setBoard(board);
        task.setPosition(request.position() != null
                ? request.position()
                : (int) taskRepository.countByBoard(board));
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTask(User user, Long taskId, TaskRequest request) {
        Task task = findOwned(user, taskId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        if (request.status() != null) {
            task.setStatus(parseStatus(request.status(), task.getStatus()));
        }
        task.setDueDate(request.dueDate());
        if (request.position() != null) {
            task.setPosition(request.position());
        }
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse moveTask(User user, Long taskId, Task.Status status, Integer position) {
        Task task = findOwned(user, taskId);
        task.setStatus(status);
        task.setPosition(position);
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public void deleteTask(User user, Long taskId) {
        Task task = findOwned(user, taskId);
        taskRepository.delete(task);
    }

    private Task findOwned(User user, Long taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> task.getBoard().getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Задание не найдено"));
    }

    private Task.Status parseStatus(String raw, Task.Status fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Task.Status.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Некорректный статус: " + raw);
        }
    }
}
