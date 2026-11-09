package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.ProfileUpdateRequest;
import com.demo.taskmanager.dto.UserResponse;
import com.demo.taskmanager.dto.UserSummaryResponse;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamGameSession;
import com.demo.taskmanager.entity.TeamMember;
import com.demo.taskmanager.entity.TeamRole;
import com.demo.taskmanager.entity.TeamSessionPresence;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.BoardRepository;
import com.demo.taskmanager.repository.GameRepository;
import com.demo.taskmanager.repository.GameResultRepository;
import com.demo.taskmanager.repository.TeamGameSessionRepository;
import com.demo.taskmanager.repository.TeamHintStateRepository;
import com.demo.taskmanager.repository.TeamInvitationRepository;
import com.demo.taskmanager.repository.TeamMemberRepository;
import com.demo.taskmanager.repository.TeamQuestionStateRepository;
import com.demo.taskmanager.repository.TeamRepository;
import com.demo.taskmanager.repository.TeamSessionPresenceRepository;
import com.demo.taskmanager.repository.UserGameProgressRepository;
import com.demo.taskmanager.repository.UserProgressRepository;
import com.demo.taskmanager.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private static final int SEARCH_LIMIT = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final GameRepository gameRepository;
    private final BoardRepository boardRepository;
    private final GameResultRepository gameResultRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserGameProgressRepository userGameProgressRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamGameSessionRepository teamGameSessionRepository;
    private final TeamSessionPresenceRepository teamSessionPresenceRepository;
    private final TeamHintStateRepository teamHintStateRepository;
    private final TeamQuestionStateRepository teamQuestionStateRepository;

    public UserService(UserRepository userRepository,
                       ImageStorageService imageStorageService,
                       GameRepository gameRepository,
                       BoardRepository boardRepository,
                       GameResultRepository gameResultRepository,
                       UserProgressRepository userProgressRepository,
                       UserGameProgressRepository userGameProgressRepository,
                       TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       TeamInvitationRepository teamInvitationRepository,
                       TeamGameSessionRepository teamGameSessionRepository,
                       TeamSessionPresenceRepository teamSessionPresenceRepository,
                       TeamHintStateRepository teamHintStateRepository,
                       TeamQuestionStateRepository teamQuestionStateRepository) {
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
        this.gameRepository = gameRepository;
        this.boardRepository = boardRepository;
        this.gameResultRepository = gameResultRepository;
        this.userProgressRepository = userProgressRepository;
        this.userGameProgressRepository = userGameProgressRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.teamGameSessionRepository = teamGameSessionRepository;
        this.teamSessionPresenceRepository = teamSessionPresenceRepository;
        this.teamHintStateRepository = teamHintStateRepository;
        this.teamQuestionStateRepository = teamQuestionStateRepository;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadRequestException("Пользователь не найден"));
    }

    public UserResponse getProfile(User user) {
        return UserResponse.from(user);
    }

    /** Список всех пользователей для панели управления супер-администратора. */
    @Transactional(readOnly = true)
    public List<UserResponse> listAllUsers() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse updateProfile(User user, ProfileUpdateRequest request) {
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            String email = request.email().trim();
            if (email.isEmpty()) {
                // Пустая строка не должна занимать уникальное значение email.
                user.setEmail(null);
            } else {
                String normalized = email.toLowerCase(Locale.ROOT);
                boolean changed = user.getEmail() == null
                        || !user.getEmail().equalsIgnoreCase(normalized);
                if (changed && userRepository.existsByEmailIgnoreCase(normalized)) {
                    throw new BadRequestException("Пользователь с таким email уже зарегистрирован");
                }
                user.setEmail(normalized);
            }
        }
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public UserResponse updateAvatar(User user, MultipartFile file) {
        user.setAvatarUrl(imageStorageService.store(file));
        userRepository.save(user);
        return UserResponse.from(user);
    }

    /** Поиск зарегистрированных пользователей для приглашения в команду. */
    public List<UserSummaryResponse> searchUsers(String search, Long currentUserId) {
        String query = search == null ? "" : search.trim();
        if (query.length() > MAX_SEARCH_LENGTH) {
            query = query.substring(0, MAX_SEARCH_LENGTH);
        }
        return userRepository.search(query, PageRequest.of(0, SEARCH_LIMIT)).stream()
                .filter(candidate -> currentUserId == null || !candidate.getId().equals(currentUserId))
                .map(UserSummaryResponse::from)
                .toList();
    }

    /**
     * Полное удаление пользователя супер-администратором.
     *
     * <p>Перед удалением связанные данные либо передаются супер-администратору
     * (игры, команды, доски), либо удаляются (результаты, прогресс, приглашения,
     * участие в командах). Активные командные сессии, которые вёл пользователь,
     * передаются другому участнику или отменяются.</p>
     */
    @Transactional
    public void deleteUser(Long userId, User currentUser) {
        if (currentUser == null || !currentUser.isSuperAdmin()) {
            throw new ForbiddenException("Удалять пользователей может только супер-администратор");
        }
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Невозможно удалить собственную учетную запись");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        // Игры и доски переходят супер-администратору: иначе внешний ключ
        // creator_id / owner_id не даст удалить пользователя.
        gameRepository.reassignCreator(userId, currentUser);
        boardRepository.reassignOwner(userId, currentUser);

        // Команды, созданные пользователем: новый создатель и ADMIN-участие.
        for (Team team : teamRepository.findByCreatorId(userId)) {
            team.setCreator(currentUser);
            teamRepository.save(team);

            TeamMember membership = teamMemberRepository
                    .findByTeamIdAndUserId(team.getId(), currentUser.getId())
                    .orElse(null);
            if (membership == null) {
                teamMemberRepository.save(new TeamMember(team, currentUser, TeamRole.ADMIN));
            } else if (membership.getRole() != TeamRole.ADMIN) {
                membership.setRole(TeamRole.ADMIN);
                teamMemberRepository.save(membership);
            }
        }

        // Результаты и прогресс прохождений принадлежат только пользователю.
        gameResultRepository.deleteByUserId(userId);
        userProgressRepository.deleteByUserId(userId);
        userGameProgressRepository.deleteByUserId(userId);

        // Приглашения, где пользователь был приглашающим или приглашённым.
        teamInvitationRepository.deleteByUserId(userId);

        // Активные командные сессии: снимаем присутствие пользователя,
        // а партии, которые он вёл, передаём другому участнику или отменяем.
        teamSessionPresenceRepository.deleteByUserId(userId);
        for (TeamGameSession session : teamGameSessionRepository.findByHostId(userId)) {
            if (!session.isActive()) {
                continue;
            }
            List<TeamSessionPresence> participants =
                    teamSessionPresenceRepository.findBySessionIdOrderByJoinedAtAsc(session.getId());
            if (participants.isEmpty()) {
                session.setStatus(TeamGameSession.STATUS_CANCELLED);
                session.setEndedAt(Instant.now());
                session.setEndReason("host_deleted");
            } else {
                TeamSessionPresence nextHost = participants.get(0);
                session.setHostId(nextHost.getUserId());
                session.setHostUsername(nextHost.getUsername());
            }
            teamGameSessionRepository.save(session);
        }

        // Обезличиваем исторические ссылки на удалённого пользователя.
        teamHintStateRepository.clearRevealedByUserId(userId);
        teamQuestionStateRepository.clearSolvedByUserId(userId);

        // Убираем пользователя из всех команд и удаляем саму учётную запись.
        teamMemberRepository.deleteByUserId(userId);
        userRepository.delete(target);
    }

    public Path resolveFile(String filename) {
        return imageStorageService.resolve(filename);
    }
}
