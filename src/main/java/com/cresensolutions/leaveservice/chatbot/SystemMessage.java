package com.cresensolutions.leaveservice.chatbot;

public record SystemMessage(String content) {

    public static SystemMessage of(String content) {
        return new SystemMessage(content);
    }
}
