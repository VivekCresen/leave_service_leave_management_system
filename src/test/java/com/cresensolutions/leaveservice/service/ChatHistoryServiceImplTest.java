package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.ChatHistory;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.ChatHistoryRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.Impl.ChatHistoryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    @Mock private ChatHistoryRepository chatHistoryRepository;
    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks
    private ChatHistoryServiceImpl chatHistoryService;

    private UserProfile user;
    private ChatHistoryService.QaPair qaPair;

    @BeforeEach
    void setUp() throws Exception {
        // ObjectMapper is not a mock — inject real instance
        Field omField = ChatHistoryServiceImpl.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(chatHistoryService, new ObjectMapper().findAndRegisterModules());

        user = new UserProfile();
        setField(user, "id", 1L);
        setField(user, "userName", "john");
        setField(user, "emailId", "john@example.com");

        qaPair = new ChatHistoryService.QaPair(
            "What is my leave balance?",
            "You have 10 days remaining.",
            "direct_db",
            120L,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ── null / blank username guards ──────────────────────────────────────────

    @Test
    void addQaPair_nullUsername_skips() {
        chatHistoryService.addQaPair(null, null, false, qaPair);
        verifyNoInteractions(userProfileRepository, chatHistoryRepository);
    }

    @Test
    void addQaPair_blankUsername_skips() {
        chatHistoryService.addQaPair("   ", null, false, qaPair);
        verifyNoInteractions(userProfileRepository, chatHistoryRepository);
    }

    @Test
    void addQaPair_nullQaPair_skips() {
        chatHistoryService.addQaPair("john", null, false, null);
        verifyNoInteractions(userProfileRepository, chatHistoryRepository);
    }

    // ── user not found ────────────────────────────────────────────────────────

    @Test
    void addQaPair_userNotFound_skips() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.empty());

        chatHistoryService.addQaPair("john", null, false, qaPair);

        verifyNoInteractions(chatHistoryRepository);
    }

    // ── new history record created ────────────────────────────────────────────

    @Test
    void addQaPair_noExistingHistory_createsNewRecord() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        ChatHistory saved = captor.getValue();
        assertThat(saved.getTotalQaPairs()).isEqualTo(1);
        assertThat(saved.getTotalSessions()).isEqualTo(1); // new conversation
        assertThat(saved.getConversations()).contains("What is my leave balance?");
    }

    // ── existing history updated ──────────────────────────────────────────────

    @Test
    void addQaPair_existingHistory_appendsToSameConversation() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations("{\"chat_001\":[]}");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getConversations()).contains("chat_001");
    }

    // ── explicit conversationId ───────────────────────────────────────────────

    @Test
    void addQaPair_withExplicitConversationId_usesIt() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", "chat_005", false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getConversations()).contains("chat_005");
    }

    // ── newConversation flag forces a new session ─────────────────────────────

    @Test
    void addQaPair_newConversationFlag_createsNewSession() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations("{\"chat_001\":[{\"question\":\"old question\"}]}");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, true, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // Should have created chat_002
        assertThat(captor.getValue().getConversations()).contains("chat_002");
    }

    // ── default interface method ──────────────────────────────────────────────

    @Test
    void addQaPair_defaultMethod_delegatesToFullMethod() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // default method: addQaPair(username, qaPair)
        chatHistoryService.addQaPair("john", qaPair);

        verify(chatHistoryRepository).save(any());
    }

    // ── legacy array format migration ─────────────────────────────────────────

    @Test
    void addQaPair_legacyArrayConversations_migratesAndSaves() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String legacyJson = "[{\"title\":\"Old session\",\"qa_pairs\":[{" +
            "\"question\":{\"content\":\"old q\"}," +
            "\"answer\":{\"content\":\"old a\",\"source\":\"ollama\",\"latency_ms\":100}," +
            "\"asked_at\":\"2024-01-01T10:00:00+00:00\"," +
            "\"answered_at\":\"2024-01-01T10:00:01+00:00\"" +
            "}]}]";

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations(legacyJson);
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // migrated — should contain the new question
        assertThat(captor.getValue().getConversations()).contains("What is my leave balance?");
    }

    // ── corrupt JSON resets gracefully ────────────────────────────────────────

    @Test
    void addQaPair_corruptJson_resetsAndSaves() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations("NOT_VALID_JSON{{{{");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        verify(chatHistoryRepository).save(any());
    }

    // ── long question title truncation ────────────────────────────────────────

    @Test
    void addQaPair_longQuestion_titleTruncated() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String longQuestion = "A".repeat(200);
        ChatHistoryService.QaPair longQaPair = new ChatHistoryService.QaPair(
            longQuestion, "answer", "direct_db", 50L, OffsetDateTime.now(), OffsetDateTime.now()
        );

        chatHistoryService.addQaPair("john", null, false, longQaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // title should be truncated to 80 chars + "..."
        assertThat(captor.getValue().getConversations()).contains("...");
    }

    // ── active session meta reuse (non-expired) ───────────────────────────────

    @Test
    void addQaPair_activeSessionMeta_reusesSameConversation() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First call — creates chat_001 and stores it in activeSessionMeta
        chatHistoryService.addQaPair("john", null, false, qaPair);

        ArgumentCaptor<ChatHistory> captor1 = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository, times(1)).save(captor1.capture());
        String firstConversations = captor1.getValue().getConversations();
        assertThat(firstConversations).contains("chat_001");

        // Second call — no explicit conversationId, not newConversation
        // activeSessionMeta is set, session is not expired → reuses chat_001
        ChatHistory existing = captor1.getValue();
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        ChatHistoryService.QaPair secondPair = new ChatHistoryService.QaPair(
            "Second question", "Second answer", "direct_db", 80L,
            OffsetDateTime.now(), OffsetDateTime.now()
        );
        chatHistoryService.addQaPair("john", null, false, secondPair);

        ArgumentCaptor<ChatHistory> captor2 = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository, times(2)).save(captor2.capture());
        // Both entries should be in chat_001
        assertThat(captor2.getValue().getConversations()).contains("chat_001");
        assertThat(captor2.getValue().getConversations()).contains("Second question");
    }

    // ── explicit conversationId that already exists + newConversation=false ──

    @Test
    void addQaPair_existingConversationId_notNew_touchesMeta() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations("{\"chat_005\":[{\"question\":\"old\",\"chatTitle\":\"old\"}]}");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First call to populate activeSessionMeta with chat_005
        chatHistoryService.addQaPair("john", "chat_005", false, qaPair);

        // Second call with same conversationId — meta.touch() is called
        ChatHistory updated = new ChatHistory(user);
        updated.setConversations("{\"chat_005\":[{\"question\":\"old\",\"chatTitle\":\"old\"}]}");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(updated));

        chatHistoryService.addQaPair("john", "chat_005", false, qaPair);

        verify(chatHistoryRepository, times(2)).save(any());
    }

    // ── normalizeConversationId — invalid format returns null ─────────────────

    @Test
    void addQaPair_invalidConversationIdFormat_ignored() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // "session-abc" doesn't match "^chat_\\d+$" → normalizeConversationId returns null
        // → falls through to create a new conversation
        chatHistoryService.addQaPair("john", "session-abc", false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // Should have created chat_001 (new conversation)
        assertThat(captor.getValue().getConversations()).contains("chat_001");
    }

    // ── newConversation=true with explicit valid conversationId ───────────────

    @Test
    void addQaPair_newConversationTrue_withValidConversationId_createsNew() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations("{\"chat_003\":[{\"question\":\"q\",\"chatTitle\":\"t\"}]}");
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // chat_003 exists + newConversation=true → should create chat_004
        chatHistoryService.addQaPair("john", "chat_003", true, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getConversations()).contains("chat_004");
    }

    // ── legacy migration — qa_id override ────────────────────────────────────

    @Test
    void addQaPair_legacyArrayWithQaId_preservesQaId() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String legacyJson = "[{\"title\":\"Session 1\",\"qa_pairs\":[{" +
            "\"qa_id\":\"custom-id-123\"," +
            "\"question\":{\"content\":\"legacy question\"}," +
            "\"answer\":{\"content\":\"legacy answer\",\"source\":\"ollama\",\"latency_ms\":50}," +
            "\"asked_at\":\"2024-06-01T09:00:00+00:00\"," +
            "\"answered_at\":\"2024-06-01T09:00:01+00:00\"" +
            "}]}]";

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations(legacyJson);
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // The migrated entry should have the custom qa_id as request_id
        assertThat(captor.getValue().getConversations()).contains("custom-id-123");
    }

    // ── parseOffsetDateTime — invalid date string falls back to now ───────────

    @Test
    void addQaPair_legacyArrayWithInvalidDates_fallsBackToNow() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String legacyJson = "[{\"title\":\"Session\",\"qa_pairs\":[{" +
            "\"question\":{\"content\":\"q\"}," +
            "\"answer\":{\"content\":\"a\",\"source\":\"ollama\",\"latency_ms\":0}," +
            "\"asked_at\":\"NOT_A_DATE\"," +
            "\"answered_at\":\"ALSO_NOT_A_DATE\"" +
            "}]}]";

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations(legacyJson);
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw — invalid dates fall back to OffsetDateTime.now()
        chatHistoryService.addQaPair("john", null, false, qaPair);

        verify(chatHistoryRepository).save(any());
    }

    // ── buildChatTitle — blank question uses default title ────────────────────

    @Test
    void addQaPair_blankQuestion_usesDefaultTitle() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChatHistoryService.QaPair blankQPair = new ChatHistoryService.QaPair(
            "   ", "answer", "direct_db", 10L, OffsetDateTime.now(), OffsetDateTime.now()
        );

        chatHistoryService.addQaPair("john", null, false, blankQPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getConversations()).contains("Chat session");
    }

    // ── null conversations string resets gracefully ───────────────────────────

    @Test
    void addQaPair_nullConversationsString_resetsAndSaves() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        ChatHistory existing = new ChatHistory(user);
        existing.setConversations(null);
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", null, false, qaPair);

        verify(chatHistoryRepository).save(any());
    }

    // ── username with leading/trailing whitespace is trimmed ─────────────────

    @Test
    void addQaPair_usernameWithWhitespace_isTrimmed() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("  john  ", null, false, qaPair);

        verify(userProfileRepository).findByUserNameIgnoreCase("john");
        verify(chatHistoryRepository).save(any());
    }

    // ── chatTitle preserved from first entry on subsequent Q&A ───────────────

    @Test
    void addQaPair_secondEntryInConversation_preservesChatTitle() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        // Existing conversation with one entry that has a chatTitle
        String existingJson = "{\"chat_001\":[{\"question\":\"First question\",\"chatTitle\":\"First question\"}]}";
        ChatHistory existing = new ChatHistory(user);
        existing.setConversations(existingJson);
        when(chatHistoryRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(chatHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatHistoryService.addQaPair("john", "chat_001", false, qaPair);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(captor.capture());
        // The chatTitle of the second entry should match the first entry's title
        assertThat(captor.getValue().getConversations()).contains("First question");
    }
}
