package com.demo.taskmanager.service;

import com.demo.taskmanager.dto.InvitationResponse;
import com.demo.taskmanager.dto.InviteRequest;
import com.demo.taskmanager.dto.TeamDetailsResponse;
import com.demo.taskmanager.dto.TeamRequest;
import com.demo.taskmanager.dto.TeamResponse;
import com.demo.taskmanager.entity.InvitationStatus;
import com.demo.taskmanager.entity.Team;
import com.demo.taskmanager.entity.TeamInvitation;
import com.demo.taskmanager.entity.TeamMember;
import com.demo.taskmanager.entity.TeamRole;
import com.demo.taskmanager.entity.User;
import com.demo.taskmanager.exception.BadRequestException;
import com.demo.taskmanager.exception.ForbiddenException;
import com.demo.taskmanager.exception.NotFoundException;
import com.demo.taskmanager.repository.GameResultRepository;
import com.demo.taskmanager.repository.TeamInvitationRepository;
import com.demo.taskmanager.repository.TeamMemberRepository;
import com.demo.taskmanager.repository.TeamRepository;
import com.demo.taskmanager.repository.TeamHintStateRepository;
import com.demo.taskmanager.repository.TeamLevelStatRepository;
import com.demo.taskmanager.repository.TeamQuestionStateRepository;
import com.demo.taskmanager.repository.TeamSessionEventRepository;
import com.demo.taskmanager.repository.TeamSessionPresenceRepository;
import com.demo.taskmanager.repository.TeamSessionProgressRepository;
import com.demo.taskmanager.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_URL_LENGTH = 500;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamInvitationRepository invitationRepository;
    private final GameResultRepository resultRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TeamSessionPresenceRepository teamSessionPresenceRepository;
    private final TeamSessionProgressRepository teamSessionProgressRepository;
    private final TeamSessionEventRepository teamSessionEventRepository;
    private final TeamQuestionStateRepository teamQuestionStateRepository;
    private final TeamHintStateRepository teamHintStateRepository;
    private final TeamLevelStatRepository teamLevelStatRepository;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository memberRepository,
                       TeamInvitationRepository invitationRepository,
                       GameResultRepository resultRepository,
                       UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate,
                       TeamSessionPresenceRepository teamSessionPresenceRepository,
                       TeamSessionProgressRepository teamSessionProgressRepository,
                       TeamSessionEventRepository teamSessionEventRepository,
                       TeamQuestionStateRepository teamQuestionStateRepository,
                       TeamHintStateRepository teamHintStateRepository,
                       TeamLevelStatRepository teamLevelStatRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.teamSessionPresenceRepository = teamSessionPresenceRepository;
        this.teamSessionProgressRepository = teamSessionProgressRepository;
        this.teamSessionEventRepository = teamSessionEventRepository;
        this.teamQuestionStateRepository = teamQuestionStateRepository;
        this.teamHintStateRepository = teamHintStateRepository;
        this.teamLevelStatRepository = teamLevelStatRepository;
    }

    /** Все команды платформы, отсортированные по рейтингу (победам). */
    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams(User currentUser) {
        // EntityGraph с коллекцией может вернуть дубли roots — убираем их, сохраняя порядок
        Map<Long, Team> unique = new LinkedHashMap<>();
        teamRepository.findAllWithDetails().forEach(team -> unique.putIfAbsent(team.getId(), team));
        return unique.values().stream()
                .map(team -> TeamResponse.from(team, currentUser.getId()))
                .toList();
    }

    /** Детальная карточка команды доступна всем авторизованным пользователям. */
    @Transactional(readOnly = true)
    public TeamDetailsResponse getTeam(Long teamId, User currentUser) {
        return toDetails(findTeam(teamId), currentUser);
    }

    /** Изменить название и/или аватар может создатель команды или супер-администратор. */
    @Transactional
    public TeamDetailsResponse updateTeam(Long teamId, TeamRequest request, User currentUser) {
        Team team = findTeam(teamId);
        requireCreatorOrSuperAdmin(team, currentUser);
        if (request == null) {
            throw new BadRequestException("Некорректные данные команды");
        }
        if (request.name() != null) {
            team.setName(validateName(request.name()));
        }
        if (request.avatarUrl() != null) {
            String avatar = request.avatarUrl().trim();
            team.setAvatarUrl(avatar.isEmpty() ? null : validateAvatarUrl(avatar));
        }
        teamRepository.save(team);
        return toDetails(team, currentUser);
    }

    /** Удалить команду вместе с составом и приглашениями может создатель или супер-администратор. */
    @Transactional
    public void deleteTeam(Long teamId, User currentUser) {
        Team team = findTeam(teamId);
        requireCreatorOrSuperAdmin(team, currentUser);
        // Сессии командных игр и их служебные записи удаляем явно:
        // иначе внешние ключи не дают удалить команду.
        for (com.demo.taskmanager.entity.TeamGameSession session : team.getGameSessions()) {
            teamSessionPresenceRepository.deleteBySessionId(session.getId());
            teamSessionProgressRepository.deleteBySessionId(session.getId());
            teamSessionEventRepository.deleteBySessionId(session.getId());
            teamQuestionStateRepository.deleteBySessionId(session.getId());
            teamHintStateRepository.deleteBySessionId(session.getId());
            teamLevelStatRepository.deleteBySessionId(session.getId());
        }
        teamRepository.delete(team);
    }

    /** Создать команду: создатель сразу становится её администратором. */
    @Transactional
    public TeamResponse createTeam(TeamRequest request, User creator) {
        if (request == null) {
            throw new BadRequestException("Некорректные данные команды");
        }
        String name = validateName(request.name());

        Team team = new Team();
        team.setName(name);
        team.setAvatarUrl(validateAvatarUrl(request.avatarUrl()));
        team.setCreator(creator);
        team.setWins(0);
        team.addMember(new TeamMember(team, creator, TeamRole.ADMIN));
        teamRepository.save(team);
        return TeamResponse.from(team, creator.getId());
    }

    /** Пригласить пользователя может только создатель команды. */
    @Transactional
    public InvitationResponse invite(Long teamId, User currentUser, InviteRequest request) {
        Team team = findTeam(teamId);
        requireCreator(team, currentUser);

        if (request == null || request.userId() == null) {
            throw new BadRequestException("Выберите пользователя для приглашения");
        }
        if (request.userId().equals(currentUser.getId())) {
            throw new BadRequestException("Нельзя пригласить самого себя");
        }
        User invitee = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (memberRepository.existsByTeamIdAndUserId(teamId, invitee.getId())) {
            throw new BadRequestException("Пользователь уже состоит в этой команде");
        }
        if (invitationRepository.existsByTeamIdAndInviteeIdAndStatus(
                teamId, invitee.getId(), InvitationStatus.PENDING)) {
            throw new BadRequestException("Приглашение этому пользователю уже отправлено");
        }

        TeamInvitation invitation = new TeamInvitation(team, currentUser, invitee);
        invitationRepository.save(invitation);
        InvitationResponse response = InvitationResponse.from(invitation);
        // Мгновенно сообщаем приглашённому о новом приглашении через WebSocket:
        // иначе счётчик в колокольчике обновлялся бы только раз в 30 секунд.
        notifyInvitation(invitee.getId(), response);
        return response;
    }

    /** Канал пользователя: /topic/user_invitations_{userId}. */
    private static final String INVITATION_TOPIC_PREFIX = "/topic/user_invitations_";

    /** Отправить приглашённому событие о новом приглашении. */
    private void notifyInvitation(Long inviteeId, InvitationResponse invitation) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "INVITATION_RECEIVED");
            payload.put("invitation", invitation);
            messagingTemplate.convertAndSend(INVITATION_TOPIC_PREFIX + inviteeId, payload);
        } catch (Exception e) {
            // доставка в реальном времени — вспомогательная, ошибка не должна ломать приглашение
        }
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> incomingInvitations(User user) {
        return invitationRepository
                .findByInviteeIdAndStatusOrderByCreatedAtDesc(user.getId(), InvitationStatus.PENDING)
                .stream()
                .map(InvitationResponse::from)
                .toList();
    }

    /** Принять приглашение: пользователь становится участником команды. */
    @Transactional
    public InvitationResponse acceptInvitation(Long invitationId, User user) {
        TeamInvitation invitation = findPendingInvitation(invitationId, user);
        Team team = invitation.getTeam();
        if (!memberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())) {
            memberRepository.save(new TeamMember(team, user, TeamRole.MEMBER));
        }
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.save(invitation);
        return InvitationResponse.from(invitation);
    }

    /** Отклонить приглашение. */
    @Transactional
    public InvitationResponse declineInvitation(Long invitationId, User user) {
        TeamInvitation invitation = findPendingInvitation(invitationId, user);
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.save(invitation);
        return InvitationResponse.from(invitation);
    }

    /** Исключить участника может только создатель команды. */
    @Transactional
    public void removeMember(Long teamId, Long userId, User currentUser) {
        Team team = findTeam(teamId);
        requireCreator(team, currentUser);
        if (team.getCreator().getId().equals(userId)) {
            throw new BadRequestException("Нельзя исключить создателя команды");
        }
        TeamMember member = memberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new NotFoundException("Участник не найден"));
        memberRepository.delete(member);
    }

    /**
     * Покинуть команду. Если уходит создатель, права переходят самому раннему
     * участнику; если других участников нет — команда удаляется.
     */
    @Transactional
    public void leaveTeam(Long teamId, User currentUser) {
        Team team = findTeam(teamId);
        TeamMember member = memberRepository.findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() -> new BadRequestException("Вы не состоите в этой команде"));

        if (!team.getCreator().getId().equals(currentUser.getId())) {
            memberRepository.delete(member);
            return;
        }

        List<TeamMember> others = memberRepository.findByTeamIdOrderByJoinedAtAscIdAsc(teamId)
                .stream()
                .filter(candidate -> !candidate.getUser().getId().equals(currentUser.getId()))
                .toList();
        if (others.isEmpty()) {
            teamRepository.delete(team);
            return;
        }

        TeamMember successor = others.get(0);
        successor.setRole(TeamRole.ADMIN);
        memberRepository.save(successor);
        team.setCreator(successor.getUser());
        teamRepository.save(team);
        memberRepository.delete(member);
    }

    /** Начисление рейтинга: +1 каждой команде победителя. */
    @Transactional
    public void registerWin(User user) {
        if (user == null) return;
        teamRepository.incrementWinsForMember(user.getId());
    }

    @Transactional(readOnly = true)
    public List<com.demo.taskmanager.dto.TeamMemberResponse> getMembers(Team team) {
        if (team == null) return List.of();
        List<TeamMember> members = memberRepository.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId());
        members.sort(java.util.Comparator.comparing((TeamMember member) -> member.getRole() == TeamRole.ADMIN).reversed()
                .thenComparing(member -> member.getUser() == null ? "" : member.getUser().getUsername()));
        return members.stream()
                .filter(member -> member.getUser() != null)
                .map(com.demo.taskmanager.dto.TeamMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long teamId, User user) {
        if (teamId == null || user == null) return false;
        return memberRepository.findByTeamIdAndUserId(teamId, user.getId()).isPresent();
    }

    /** id команд, в которых состоит пользователь. */
    @Transactional(readOnly = true)
    public List<Long> teamIdsOf(User user) {
        if (user == null) return List.of();
        return memberRepository.findByUserId(user.getId()).stream()
                .map(member -> member.getTeam() == null ? null : member.getTeam().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Team findTeamEntity(Long id) {
        return findTeam(id);
    }

    private Team findTeam(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Команда не найдена"));
    }

    private TeamDetailsResponse toDetails(Team team, User currentUser) {
        List<Long> memberIds = team.getMembers().stream()
                .map(member -> member.getUser().getId())
                .toList();
        long gamesPlayed = memberIds.isEmpty()
                ? 0
                : resultRepository.countMemberGames(memberIds);
        return TeamDetailsResponse.from(team, currentUser.getId(), gamesPlayed);
    }

    private String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Введите название команды");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("Название команды не должно быть длиннее "
                    + MAX_NAME_LENGTH + " символов");
        }
        return name;
    }

    private TeamInvitation findPendingInvitation(Long invitationId, User user) {
        TeamInvitation invitation = invitationRepository.findByIdAndInviteeId(invitationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Приглашение не найдено"));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Приглашение уже обработано");
        }
        return invitation;
    }

    private void requireCreator(Team team, User user) {
        if (user == null || team.getCreator() == null
                || !team.getCreator().getId().equals(user.getId())) {
            throw new ForbiddenException("Действие доступно только создателю команды");
        }
    }

    /** Управление командой (название/аватар, удаление) доступно создателю и SUPERADMIN. */
    private void requireCreatorOrSuperAdmin(Team team, User user) {
        if (user == null) {
            throw new ForbiddenException("Действие доступно только создателю команды или супер-администратору");
        }
        if (user.isSuperAdmin()) {
            return;
        }
        requireCreator(team, user);
    }

    /** Разрешаем только http(s)-ссылки и файлы, загруженные через /api/files. */
    private String validateAvatarUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        if (url.length() > MAX_AVATAR_URL_LENGTH) {
            throw new BadRequestException("Ссылка на аватар слишком длинная");
        }
        boolean allowed = url.startsWith("https://") || url.startsWith("http://")
                || url.startsWith("/api/files/");
        if (!allowed) {
            throw new BadRequestException("Аватар должен быть ссылкой http(s) или загруженным файлом");
        }
        return url;
    }
}
