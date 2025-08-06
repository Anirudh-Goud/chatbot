package com.llama.chatbot.service.exception;

public class DatabaseServiceException extends RuntimeException {
    public DatabaseServiceException(String message) {
        super(message);
    }

    public DatabaseServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}