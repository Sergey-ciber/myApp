package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.BoardRequest;
import com.demo.taskmanager.dto.BoardResponse;
import com.demo.taskmanager.entity.Board;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.BoardRepository;
import com.demo.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BoardService {

    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;

    public BoardService(BoardRepository boardRepository, TaskRepository taskRepository) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> listBoards(User user) {
        return boardRepository.findByOwnerOrderByCreatedAtDesc(user).stream()
                .map(board -> BoardResponse.from(board, taskRepository.countByBoard(board)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(User user, Long boardId) {
        Board board = findOwned(user, boardId);
        return BoardResponse.from(board, taskRepository.countByBoard(board));
    }

    @Transactional
    public BoardResponse createBoard(User user, BoardRequest request) {
        Board board = new Board();
        board.setTitle(request.title().trim());
        board.setDescription(request.description());
        board.setColor(request.color() == null || request.color().isBlank() ? "#0079bf" : request.color());
        board.setOwner(user);
        boardRepository.save(board);
        return BoardResponse.from(board, 0);
    }

    @Transactional
    public BoardResponse updateBoard(User user, Long boardId, BoardRequest request) {
        Board board = findOwned(user, boardId);
        board.setTitle(request.title().trim());
        board.setDescription(request.description());
        if (request.color() != null && !request.color().isBlank()) {
            board.setColor(request.color());
        }
        boardRepository.save(board);
        return BoardResponse.from(board, taskRepository.countByBoard(board));
    }

    @Transactional
    public void deleteBoard(User user, Long boardId) {
        Board board = findOwned(user, boardId);
        taskRepository.deleteAll(taskRepository.findByBoardOrderByPositionAsc(board));
        boardRepository.delete(board);
    }

    public Board findOwned(User user, Long boardId) {
        return boardRepository.findByIdAndOwner(boardId, user)
                .orElseThrow(() -> new NotFoundException("Доска не найдена"));
    }
}
