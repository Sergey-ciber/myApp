package com.demo.taskmanager.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TeamGameStateResponse(
        Long sessionId,
        String status,
        int currentLevelIndex,
        GamePlayResponse game,
        Map<String, String> solved,
        double penaltyTime,
        Instant startTime,
        List<Map<String, Object>> events,
        List<String> onlineUserIds,
        Map<String, String> memberNames
) {
}
