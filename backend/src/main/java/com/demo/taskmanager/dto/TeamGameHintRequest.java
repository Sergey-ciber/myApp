package com.demo.taskmanager.dto;

public record TeamGameHintRequest(Long sessionId, Long questionId, Integer hintIndex) {
}
