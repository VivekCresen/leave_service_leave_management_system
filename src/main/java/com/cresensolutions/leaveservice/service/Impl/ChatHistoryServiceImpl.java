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

        ArrayNode conversationEntries = conversations.withArray(targetConversationId);
        int nextQuestionId = conversationEntries.size() + 1;
        String chatTitle = conversationEntries.size() > 0
            ? conversationEntries.get(0).path("chatTitle").asText(buildChatTitle(qaPair.question()))
            : buildChatTitle(qaPair.question());

        conversationEntries.add(buildConversationEntry(targetConversationId, nextQuestionId, chatTitle, userProfile, qaPair));

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

    private ObjectNode buildConversationEntry(
        String conversationId,
        int questionId,
        String chatTitle,
        UserProfile userProfile,
        QaPair qaPair
    ) {
        ObjectNode entry = objectMapper.createObjectNode();
        ArrayNode answerArray = objectMapper.createArrayNode();
        ObjectNode answerNode = objectMapper.createObjectNode();
        answerNode.put("Text", qaPair.answer());
        answerNode.putNull("Table");
        answerArray.add(answerNode);

        entry.set("answer", answerArray);
        entry.put("profile", "localhost");
        if (userProfile.getId() != null) {
            entry.put("user_id", userProfile.getId());
        } else {
            entry.putNull("user_id");
        }
        entry.put("chatDate", qaPair.askedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        entry.put("question", qaPair.question());
        entry.put("chatTitle", chatTitle);
        entry.put("request_id", "req_" + System.currentTimeMillis() + "_" + questionId);
        entry.put("question_id", questionId);
        entry.put("response_type", "text");
        entry.put("conversation_id", conversationId);
        entry.put("request_timestamp", qaPair.askedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        entry.put("response_timestamp", qaPair.answeredAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        entry.put("username", userProfile.getUserName());
        entry.put("source", qaPair.source());
        entry.put("latency_ms", qaPair.latencyMs());
        return entry;
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
            ArrayNode entries = objectMapper.createArrayNode();
            String title = sessionNode.path("title").asText(LeaveConstants.CHATBOT_SESSION_DEFAULT_TITLE);
            JsonNode qaPairs = sessionNode.path("qa_pairs");

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

                    ObjectNode entry = buildConversationEntry(
                        conversationId,
                        questionIndex++,
                        title,
                        userProfile,
                        migratedQaPair
                    );
                    if (qaNode.hasNonNull("qa_id")) {
                        entry.put("request_id", qaNode.path("qa_id").asText());
                    }
                    entries.add(entry);
                }
            }

            migrated.set(conversationId, entries);
        }

        return migrated;
    }

    private String resolveConversationId(
        String username,
        String requestedConversationId,
        boolean newConversation,
        ObjectNode conversations
    ) {
        String normalizedRequestedId = normalizeConversationId(requestedConversationId);
        if (normalizedRequestedId != null) {
          if (!conversations.has(normalizedRequestedId)) {
              return normalizedRequestedId;
          }
          if (!newConversation) {
              ActiveSessionMeta meta = activeSessionMeta.get(username);
              if (meta != null) {
                  meta.touch();
              }
              return normalizedRequestedId;
          }
        }

        ActiveSessionMeta meta = activeSessionMeta.get(username);
        if (!newConversation && meta != null && !meta.isExpired() && conversations.has(meta.sessionId)) {
            meta.touch();
            return meta.sessionId;
        }

        if (!newConversation) {
            String latestConversationId = findLatestConversationId(conversations);
            if (latestConversationId != null) {
                return latestConversationId;
            }
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
