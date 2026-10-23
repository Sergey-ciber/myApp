package com.demo.taskmanager.exception;

/** Действие разрешено только автору объекта (например, создателю игры). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
