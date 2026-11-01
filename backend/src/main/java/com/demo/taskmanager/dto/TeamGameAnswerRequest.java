package com.demo.taskmanager.dto;

public record TeamGameAnswerRequest(Long sessionId, Long questionId, String answer) {
}
