package com.llama.chatbot.controller;

import com.llama.chatbot.service.exception.AiServiceException;
import com.llama.chatbot.service.exception.DatabaseServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiServiceException(AiServiceException ex) {
        // AI service failures are internal server errors (5xx)
        Map<String, Object> body = Map.of(
                "success", false,
                "error", "AI service failed",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DatabaseServiceException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseServiceException(DatabaseServiceException ex) {
        Map<String, Object> body = Map.of(
                "success", false,
                "error", "Database operation failed",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Invalid arguments are client errors (400)
        Map<String, Object> body = Map.of(
                "success", false,
                "error", "Invalid request",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }
}