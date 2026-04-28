package com.cresensolutions.leaveservice.chatbot;

import java.util.ArrayList;
import java.util.List;


public class Prompt {

    private final SystemMessage systemMessage;
    private final List<UserMessage> userMessages;

    private Prompt(SystemMessage systemMessage, List<UserMessage> userMessages) {
        this.systemMessage = systemMessage;
        this.userMessages = new ArrayList<>(userMessages);
    }

    public static Prompt create(SystemMessage systemMessage, UserMessage userMessage) {
        return new Prompt(systemMessage, List.of(userMessage));
    }

    public SystemMessage getSystemMessage() {
        return systemMessage;
    }

    public List<UserMessage> getUserMessages() {
        return userMessages;
    }

   
    public String toSingleString() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemMessage.content()).append("\n\n");
        for (UserMessage msg : userMessages) {
            sb.append("Question: ").append(msg.content()).append("\n");
        }
        return sb.toString();
    }

  
    public List<ChatMessage> toChatMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemMessage.content()));
        for (UserMessage msg : userMessages) {
            messages.add(new ChatMessage("user", msg.content()));
        }
        return messages;
    }

    public record ChatMessage(String role, String content) {}
}
