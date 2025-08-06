package com.llama.chatbot.service.dto;

import java.util.List;

public record OllamaChatRequest(String model, List<Message> messages, boolean stream) {
    public record Message(String role, String content) {}
}