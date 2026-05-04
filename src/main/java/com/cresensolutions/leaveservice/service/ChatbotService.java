package com.cresensolutions.leaveservice.service;

public interface ChatbotService {

    String chat(String userMessage);

    String chat(String userMessage, String currentUsername);

    String chat(String userMessage, String currentUsername, String requestId);

    String chat(String userMessage, String currentUsername, String requestId, String conversationId, boolean newConversation);

    String chat(String userMessage, String currentUsername, String role, String requestId, String conversationId, boolean newConversation);

    boolean cancelRequest(String requestId);
}
