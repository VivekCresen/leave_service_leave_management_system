package com.cresensolutions.leaveservice.service;

import java.time.OffsetDateTime;

public interface ChatHistoryService {

    default void addQaPair(String username, QaPair qaPair) {
        addQaPair(username, null, false, qaPair);
    }

    void addQaPair(String username, String conversationId, boolean newConversation, QaPair qaPair);

    record QaPair(
        String question,
        String answer,
        String source,
        long latencyMs,
        OffsetDateTime askedAt,
        OffsetDateTime answeredAt
    ) {}
}
