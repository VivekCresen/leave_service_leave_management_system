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
}
