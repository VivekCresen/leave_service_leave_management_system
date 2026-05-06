package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.model.ChatHistory;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.ChatHistoryRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.ChatHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, ActiveSessionMeta> activeSessionMeta = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void addQaPair(String username, String conversationId, boolean newConversation, QaPair qaPair) {
        if (username == null || username.isBlank() || qaPair == null) {
            return;
        }

        String user = username.trim();
        Optional<UserProfile> userOpt = userProfileRepository.findByUserNameIgnoreCase(user);
        if (userOpt.isEmpty()) {
            log.warn("ChatHistory: user '{}' not found, skipping save", user);
            return;
        }

        UserProfile userProfile = userOpt.get();
        ChatHistory history = chatHistoryRepository.findByUserId(userProfile.getId()).orElseGet(() -> new ChatHistory(userProfile));

        ObjectNode conversations = readConversationRoot(history.getConversations(), userProfile);
        String targetConversationId = resolveConversationId(user, conversationId, newConversation, conversations);
        boolean createdNewConversation = !conversations.has(targetConversationId);

        ObjectNode conversation = getOrCreateConversation(conversations, targetConversationId, userProfile, qaPair);
        ArrayNode messages = conversation.withArray("messages");
        int nextQuestionId = messages.size() + 1;

        if (createdNewConversation) {
            conversation.put("chatTitle", buildChatTitle(qaPair.question()));
            conversation.put("chatDate", qaPair.askedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }

        messages.add(buildMessageEntry(nextQuestionId, qaPair));

        try {
            history.setConversations(objectMapper.writeValueAsString(conversations));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat history conversations", e);
        }

        if (createdNewConversation) {
            history.incrementSessions();
        }
        history.incrementQaPairs();
        chatHistoryRepository.save(history);

        activeSessionMeta.put(user, new ActiveSessionMeta(targetConversationId, userProfile.getId()));
        log.debug("ChatHistory: saved Q&A to conversation '{}' for user '{}'", targetConversationId, user);
    }

    private ObjectNode getOrCreateConversation(ObjectNode conversations, String conversationId, UserProfile userProfile, QaPair qaPair) {
        if (conversations.has(conversationId)) {
            JsonNode existing = conversations.get(conversationId);
            if (existing.isObject()) {
                ObjectNode conv = (ObjectNode) existing;
                // Migrate old { meta, messages } format to flat format
                if (conv.has("meta") && conv.get("meta").isObject()) {
                    ObjectNode flat = objectMapper.createObjectNode();
                    JsonNode meta = conv.get("meta");
                    meta.fields().forEachRemaining(e -> flat.set(e.getKey(), e.getValue()));
                    flat.set("messages", conv.has("messages") ? conv.get("messages") : objectMapper.createArrayNode());
                    conversations.set(conversationId, flat);
                    return flat;
                }
                return conv;
            }
            // Migrate old array format to flat structure
            if (existing.isArray()) {
                return migrateConversationToNewFormat((ArrayNode) existing, conversationId, userProfile);
            }
        }

        // Create new flat conversation structure
        ObjectNode conversation = objectMapper.createObjectNode();
        conversation.put("conversation_id", conversationId);
        conversation.put("username", userProfile.getUserName());
        if (userProfile.getId() != null) {
            conversation.put("user_id", userProfile.getId());
        } else {
            conversation.putNull("user_id");
        }
        conversation.put("profile", "localhost");
        conversation.put("response_type", "text");
        conversation.put("chatTitle", buildChatTitle(qaPair.question()));
        conversation.put("chatDate", qaPair.askedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        conversation.putArray("messages");
        conversations.set(conversationId, conversation);
        return conversation;
    }

    private ObjectNode migrateConversationToNewFormat(ArrayNode oldMessages, String conversationId, UserProfile userProfile) {
        ObjectNode conversation = objectMapper.createObjectNode();
        ArrayNode messages = conversation.putArray("messages");

        // Extract fields from first message if available, write flat
        if (oldMessages.size() > 0) {
            JsonNode first = oldMessages.get(0);
            conversation.put("chatTitle", first.path("chatTitle").asText(LeaveConstants.CHATBOT_SESSION_DEFAULT_TITLE));
            conversation.put("chatDate", first.path("chatDate").asText(""));
            conversation.put("username", first.path("username").asText(userProfile.getUserName()));
            conversation.put("user_id", first.path("user_id").asLong(userProfile.getId()));
            conversation.put("profile", first.path("profile").asText("localhost"));
        } else {
            conversation.put("chatTitle", LeaveConstants.CHATBOT_SESSION_DEFAULT_TITLE);
            conversation.put("chatDate", "");
            conversation.put("username", userProfile.getUserName());
            conversation.put("user_id", userProfile.getId());
            conversation.put("profile", "localhost");
        }
        conversation.put("conversation_id", conversationId);
        conversation.put("response_type", "text");

        // Migrate messages
        for (JsonNode oldMsg : oldMessages) {
            ObjectNode newMsg = objectMapper.createObjectNode();
            newMsg.put("question", oldMsg.path("question").asText(""));
            newMsg.put("answer", oldMsg.path("answer").asText(""));
            if (oldMsg.has("table")) {
                newMsg.set("table", oldMsg.get("table"));
            } else {
                newMsg.putNull("table");
            }
            newMsg.put("source", oldMsg.path("source").asText(""));
            newMsg.put("question_id", oldMsg.path("question_id").asInt(0));
            newMsg.put("request_id", oldMsg.path("request_id").asText(""));
            newMsg.put("latency_ms", oldMsg.path("latency_ms").asLong(0));
            newMsg.put("request_timestamp", oldMsg.path("request_timestamp").asText(""));
            newMsg.put("response_timestamp", oldMsg.path("response_timestamp").asText(""));
            messages.add(newMsg);
        }

        return conversation;
    }

    private ObjectNode buildMessageEntry(int questionId, QaPair qaPair) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("question", qaPair.question());

        ParsedAnswer parsed = parseAnswerForTable(qaPair.answer());
        message.put("answer", parsed.text());
        if (parsed.table() != null) {
            message.set("table", parsed.table());
        } else {
            message.putNull("table");
        }

        message.put("source", qaPair.source());
        message.put("question_id", questionId);
        message.put("request_id", "req_" + System.currentTimeMillis() + "_" + questionId);
        message.put("latency_ms", qaPair.latencyMs());
        message.put("request_timestamp", qaPair.askedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        message.put("response_timestamp", qaPair.answeredAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return message;
    }

    private ObjectNode readConversationRoot(String rawConversations, UserProfile userProfile) {
        if (rawConversations == null || rawConversations.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode parsed = objectMapper.readTree(rawConversations);
            if (parsed == null || parsed.isNull()) {
                return objectMapper.createObjectNode();
            }
            if (parsed.isObject()) {
                return (ObjectNode) parsed;
            }
            if (parsed.isArray()) {
                return migrateLegacySessions((ArrayNode) parsed, userProfile);
            }
        } catch (Exception e) {
            log.warn("ChatHistory: failed to parse existing conversations JSON, resetting history root: {}", e.getMessage());
        }

        return objectMapper.createObjectNode();
    }

    private ObjectNode migrateLegacySessions(ArrayNode sessions, UserProfile userProfile) {
        ObjectNode migrated = objectMapper.createObjectNode();
        int conversationIndex = 1;

        for (JsonNode sessionNode : sessions) {
            String conversationId = formatConversationId(conversationIndex++);
            String title = sessionNode.path("title").asText(LeaveConstants.CHATBOT_SESSION_DEFAULT_TITLE);
            JsonNode qaPairs = sessionNode.path("qa_pairs");

            ObjectNode conversation = objectMapper.createObjectNode();
            conversation.put("conversation_id", conversationId);
            conversation.put("chatTitle", title);
            conversation.put("chatDate", "");
            conversation.put("username", userProfile.getUserName());
            conversation.put("user_id", userProfile.getId());
            conversation.put("profile", "localhost");
            conversation.put("response_type", "text");
            ArrayNode messages = conversation.putArray("messages");

            if (qaPairs.isArray()) {
                int questionIndex = 1;
                for (JsonNode qaNode : qaPairs) {
                    QaPair migratedQaPair = new QaPair(
                        qaNode.path("question").path("content").asText(""),
                        qaNode.path("answer").path("content").asText(""),
                        qaNode.path("answer").path("source").asText(LeaveConstants.CHATBOT_SOURCE_OLLAMA),
                        qaNode.path("answer").path("latency_ms").asLong(0L),
                        parseOffsetDateTime(qaNode.path("asked_at").asText(null)),
                        parseOffsetDateTime(qaNode.path("answered_at").asText(null))
                    );
                    ObjectNode msg = buildMessageEntry(questionIndex++, migratedQaPair);
                    if (qaNode.hasNonNull("qa_id")) {
                        msg.put("request_id", qaNode.path("qa_id").asText());
                    }
                    messages.add(msg);
                }
            }

            migrated.set(conversationId, conversation);
        }

        return migrated;
    }

    private String resolveConversationId(
        String username,
        String requestedConversationId,
        boolean newConversation,
        ObjectNode conversations
    ) {
        // Always trust the conversationId sent by the frontend if it's valid
        String normalizedRequestedId = normalizeConversationId(requestedConversationId);
        if (normalizedRequestedId != null && !newConversation) {
            ActiveSessionMeta meta = activeSessionMeta.get(username);
            if (meta != null) meta.touch();
            return normalizedRequestedId;
        }

        // newConversation=true: create the next available id
        if (newConversation) {
            return nextConversationId(conversations);
        }

        // No conversationId from frontend — reuse active session or latest
        ActiveSessionMeta meta = activeSessionMeta.get(username);
        if (meta != null && !meta.isExpired() && conversations.has(meta.sessionId)) {
            meta.touch();
            return meta.sessionId;
        }

        String latestConversationId = findLatestConversationId(conversations);
        if (latestConversationId != null) {
            return latestConversationId;
        }

        return nextConversationId(conversations);
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }

        String trimmed = conversationId.trim();
        return trimmed.matches("^chat_\\d+$") ? trimmed : null;
    }

    private String findLatestConversationId(ObjectNode conversations) {
        int maxConversationNumber = 0;
        String latestConversationId = null;

        Iterator<String> fieldNames = conversations.fieldNames();
        while (fieldNames.hasNext()) {
            String conversationId = fieldNames.next();
            int conversationNumber = parseConversationNumber(conversationId);
            if (conversationNumber > maxConversationNumber) {
                maxConversationNumber = conversationNumber;
                latestConversationId = conversationId;
            }
        }

        return latestConversationId;
    }

    private String nextConversationId(ObjectNode conversations) {
        int maxConversationNumber = 0;
        Iterator<String> fieldNames = conversations.fieldNames();
        while (fieldNames.hasNext()) {
            maxConversationNumber = Math.max(maxConversationNumber, parseConversationNumber(fieldNames.next()));
        }
        return formatConversationId(maxConversationNumber + 1);
    }

    private int parseConversationNumber(String conversationId) {
        if (conversationId == null || !conversationId.matches("^chat_\\d+$")) {
            return 0;
        }
        return Integer.parseInt(conversationId.substring("chat_".length()));
    }

    private String formatConversationId(int conversationNumber) {
        return String.format("chat_%03d", conversationNumber);
    }

    private String buildChatTitle(String question) {
        if (question == null || question.isBlank()) {
            return LeaveConstants.CHATBOT_SESSION_DEFAULT_TITLE;
        }

        String trimmed = question.trim();
        if (trimmed.length() <= LeaveConstants.CHATBOT_SESSION_TITLE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, LeaveConstants.CHATBOT_SESSION_TITLE_MAX_LENGTH) + LeaveConstants.CHATBOT_TITLE_ELLIPSIS;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            return OffsetDateTime.now();
        }
    }

    private record ParsedAnswer(String text, JsonNode table) {}

    /**
     * If the answer contains a markdown table, extracts it into a structured JSON object:
     * { "headers": [...], "rows": [[...], [...]] }
     * The plain text portion (title line before the table) is kept in Text.
     */
    private ParsedAnswer parseAnswerForTable(String answer) {
        if (answer == null || answer.isBlank()) {
            return new ParsedAnswer(answer, null);
        }

        String[] lines = answer.split("\n");
        int headerLineIdx = -1;
        int separatorLineIdx = -1;

        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            String next = lines[i + 1].trim();
            if (line.startsWith("|") && line.endsWith("|")
                    && next.matches("\\|[-| :]+\\|")) {
                headerLineIdx = i;
                separatorLineIdx = i + 1;
                break;
            }
        }

        if (headerLineIdx == -1) {
            return new ParsedAnswer(answer, null);
        }

        // Extract text before the table
        StringBuilder textBefore = new StringBuilder();
        for (int i = 0; i < headerLineIdx; i++) {
            if (i > 0) textBefore.append("\n");
            textBefore.append(lines[i]);
        }

        // Parse headers
        String[] headers = splitTableRow(lines[headerLineIdx]);

        // Parse data rows (everything after the separator line)
        ArrayNode rowsNode = objectMapper.createArrayNode();
        for (int i = separatorLineIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank() || !line.startsWith("|")) break;
            String[] cells = splitTableRow(line);
            ArrayNode rowNode = objectMapper.createArrayNode();
            for (String cell : cells) rowNode.add(cell);
            rowsNode.add(rowNode);
        }

        ArrayNode headersNode = objectMapper.createArrayNode();
        for (String h : headers) headersNode.add(h);

        ObjectNode tableNode = objectMapper.createObjectNode();
        tableNode.set("headers", headersNode);
        tableNode.set("rows", rowsNode);

        return new ParsedAnswer(textBefore.toString().trim(), tableNode);
    }

    private String[] splitTableRow(String line) {
        // Remove leading/trailing pipes then split
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split("\\|");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static class ActiveSessionMeta {
        final String sessionId;
        final Long userId;
        OffsetDateTime lastActivity;

        ActiveSessionMeta(String sessionId, Long userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.lastActivity = OffsetDateTime.now();
        }

        boolean isExpired() {
            return OffsetDateTime.now().isAfter(lastActivity.plusMinutes(LeaveConstants.CHATBOT_SESSION_GAP_MINUTES));
        }

        void touch() {
            this.lastActivity = OffsetDateTime.now();
        }
    }
}
