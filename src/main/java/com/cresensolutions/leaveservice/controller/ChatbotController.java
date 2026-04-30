package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> request) {
        String userMessage = request.get(LeaveConstants.CHATBOT_REQUEST_MESSAGE_KEY) instanceof String
            ? (String) request.get(LeaveConstants.CHATBOT_REQUEST_MESSAGE_KEY)
            : null;
        String username = request.get(LeaveConstants.CHATBOT_REQUEST_USERNAME_KEY) instanceof String
            ? (String) request.get(LeaveConstants.CHATBOT_REQUEST_USERNAME_KEY)
            : null;
        String requestId = request.get(LeaveConstants.CHATBOT_REQUEST_ID_KEY) instanceof String
            ? (String) request.get(LeaveConstants.CHATBOT_REQUEST_ID_KEY)
            : null;
        String conversationId = request.get(LeaveConstants.CHATBOT_CONVERSATION_ID_KEY) instanceof String
            ? (String) request.get(LeaveConstants.CHATBOT_CONVERSATION_ID_KEY)
            : null;
        boolean newConversation = Boolean.parseBoolean(String.valueOf(
            request.getOrDefault(LeaveConstants.CHATBOT_NEW_CONVERSATION_KEY, false)
        ));

        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                LeaveConstants.CHATBOT_ERROR_KEY,
                LeaveConstants.CHATBOT_EMPTY_MESSAGE_ERROR
            ));
        }

        String response = chatbotService.chat(userMessage, username, requestId, conversationId, newConversation);
        return ResponseEntity.ok(Map.of(LeaveConstants.CHATBOT_RESPONSE_KEY, response));
    }

    @DeleteMapping("/cancel/{requestId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String requestId) {
        boolean cancelled = chatbotService.cancelRequest(requestId);
        return ResponseEntity.ok(Map.of(
            LeaveConstants.CHATBOT_CANCELLED_KEY, cancelled,
            LeaveConstants.CHATBOT_REQUEST_ID_KEY, requestId
        ));
    }
}
