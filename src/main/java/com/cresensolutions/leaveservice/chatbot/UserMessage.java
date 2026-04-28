package com.cresensolutions.leaveservice.chatbot;


public record UserMessage(String content) {

    public static UserMessage of(String content) {
        return new UserMessage(content);
    }
}
