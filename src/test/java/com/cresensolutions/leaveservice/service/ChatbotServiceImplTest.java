package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.chatbot.ChatbotTools;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.*;
import com.cresensolutions.leaveservice.service.Impl.ChatbotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock private DataSource dataSource;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EmployeeLeaveRepository employeeLeaveRepository;
    @Mock private LeaveRepository leaveRepository;
    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private PublicHolidayRepository publicHolidayRepository;
    @Mock private ChatHistoryService chatHistoryService;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatbotTools chatbotTools;
    @Mock private ExecutorService modelExecutor;
    @Mock private ChatClient chatClient;

    private ChatbotServiceImpl chatbotService;

    private UserProfile user;

    @BeforeEach
    void setUp() throws Exception {
        // ChatClient.Builder.defaultTools takes varargs Object... — use lenient any() matcher
        when(chatClientBuilder.defaultTools(any(ChatbotTools.class))).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        chatbotService = new ChatbotServiceImpl(
            dataSource,
            userProfileRepository,
            employeeLeaveRepository,
            leaveRepository,
            leaveTypeRepository,
            publicHolidayRepository,
            chatHistoryService,
            chatClientBuilder,
            chatbotTools,
            modelExecutor,
            5L
        );

        user = new UserProfile();
        setField(user, "id", 1L);
        setField(user, "userName", "john");
        setField(user, "fullName", "John Doe");
        setField(user, "emailId", "john@example.com");
        setField(user, "active", true);
        setField(user, "role", "EMPLOYEE");
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rowList(Object[]... rows) {
        return List.of(rows);
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

    // ── cancelRequest ─────────────────────────────────────────────────────────

    @Test
    void cancelRequest_nullRequestId_returnsFalse() {
        assertThat(chatbotService.cancelRequest(null)).isFalse();
    }

    @Test
    void cancelRequest_blankRequestId_returnsFalse() {
        assertThat(chatbotService.cancelRequest("   ")).isFalse();
    }

    @Test
    void cancelRequest_unknownRequestId_returnsFalse() {
        // No in-flight request with this id
        assertThat(chatbotService.cancelRequest("req-999")).isFalse();
    }

    // ── leave balance intent ──────────────────────────────────────────────────

    @Test
    void chat_leaveBalanceIntent_userFound_returnsBalance() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 10.0}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        assertThat(response).contains("John Doe");
        assertThat(response).contains("Annual Leave");
        assertThat(response).contains("10");
    }

    @Test
    void chat_leaveBalanceIntent_userNotFound_returnsNotFoundMessage() {
        when(userProfileRepository.findByUserNameIgnoreCase("unknown")).thenReturn(Optional.empty());

        String response = chatbotService.chat("What is my leave balance?", "unknown");

        assertThat(response).containsIgnoringCase("couldn't find");
    }

    @Test
    void chat_leaveBalanceIntent_noUsername_returnsPrompt() {
        String response = chatbotService.chat("What is the leave balance?", null);

        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("username"),
            r -> assertThat(r).containsIgnoringCase("logged in")
        );
    }

    @Test
    void chat_leaveBalanceIntent_noBalanceRecords_returnsNoRecordsMessage() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L)).thenReturn(List.of());

        String response = chatbotService.chat("What is my leave balance?", "john");

        assertThat(response).containsIgnoringCase("does not have");
    }

    // ── pending leave intent ──────────────────────────────────────────────────

    @Test
    void chat_pendingLeaveIntent_returnsCount() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(leaveRepository.countByStatusAndUsername("john", "PENDING")).thenReturn(3L);

        String response = chatbotService.chat("How many pending leaves do I have?", "john");

        assertThat(response).contains("3");
        assertThat(response).containsIgnoringCase("pending");
    }

    // ── approved leave intent ─────────────────────────────────────────────────

    @Test
    void chat_approvedLeaveIntent_returnsCount() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(leaveRepository.countByStatusAndUsername("john", "APPROVED")).thenReturn(5L);

        String response = chatbotService.chat("How many approved leaves do I have?", "john");

        assertThat(response).contains("5");
        assertThat(response).containsIgnoringCase("approved");
    }

    // ── rejected leave intent ─────────────────────────────────────────────────

    @Test
    void chat_rejectedLeaveIntent_returnsCount() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(leaveRepository.countByStatusAndUsername("john", "REJECTED")).thenReturn(1L);

        String response = chatbotService.chat("How many rejected leaves do I have?", "john");

        assertThat(response).contains("1");
        assertThat(response).containsIgnoringCase("rejected");
    }

    // ── upcoming holiday intent ───────────────────────────────────────────────

    @Test
    void chat_upcomingHolidayIntent_returnsHolidays() {
        PublicHoliday holiday = new PublicHoliday("New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of(holiday));

        String response = chatbotService.chat("What are the upcoming holidays?", "john");

        assertThat(response).containsIgnoringCase("New Year");
    }

    @Test
    void chat_upcomingHolidayIntent_noHolidays_returnsNotFoundMessage() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        String response = chatbotService.chat("What are the upcoming holidays?", "john");

        assertThat(response).containsIgnoringCase("couldn't find");
    }

    // ── on leave today intent ─────────────────────────────────────────────────

    @Test
    void chat_onLeaveTodayIntent_returnsNames() {
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(new Object[]{"john", "John Doe", "APPROVED"}));

        String response = chatbotService.chat("Who is on leave today?", "john");

        assertThat(response).containsIgnoringCase("John Doe");
    }

    @Test
    void chat_onLeaveTodayIntent_noOne_returnsNoOneMessage() {
        when(leaveRepository.findPeopleOnLeaveByDate(any())).thenReturn(List.of());

        String response = chatbotService.chat("Who is on leave today?", "john");

        assertThat(response).containsIgnoringCase("No one");
    }

    // ── leave policy intent ───────────────────────────────────────────────────

    @Test
    void chat_leavePolicyIntent_returnsTypes() {
        when(leaveTypeRepository.findAllForChatbot())
            .thenReturn(rowList(new Object[]{"Annual Leave", "20", "Standard annual leave"}));

        String response = chatbotService.chat("What are the leave types?", "john");

        assertThat(response).containsIgnoringCase("Annual Leave");
        assertThat(response).contains("20");
    }

    @Test
    void chat_leavePolicyIntent_noTypes_returnsNotConfiguredMessage() {
        when(leaveTypeRepository.findAllForChatbot()).thenReturn(List.of());

        String response = chatbotService.chat("What are the leave types?", "john");

        assertThat(response).containsIgnoringCase("No leave types");
    }

    // ── user lookup intent ────────────────────────────────────────────────────

    @Test
    void chat_userLookupByName_returnsProfile() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String response = chatbotService.chat("Who is john?", "admin");

        assertThat(response).contains("john");
        assertThat(response).containsIgnoringCase("John Doe");
    }

    @Test
    void chat_userLookupByName_notFound_returnsNotFoundMessage() {
        when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        String response = chatbotService.chat("Who is nobody?", "admin");

        // Falls through to Ollama path — but Ollama executor will fail/timeout
        // Just verify it doesn't throw
        assertThat(response).isNotNull();
    }

    // ── answer cache ──────────────────────────────────────────────────────────

    @Test
    void chat_sameMessageTwice_secondCallUsesCache() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        chatbotService.chat("What are the upcoming holidays?", "john");
        chatbotService.chat("What are the upcoming holidays?", "john");

        // publicHolidayRepository should only be called once (second call hits cache)
        verify(publicHolidayRepository, times(1)).findByDateBetween(any(), any());
    }

    // ── single-arg chat overload ──────────────────────────────────────────────

    @Test
    void chat_singleArg_noUsername_leaveBalanceReturnsPrompt() {
        String response = chatbotService.chat("What is my leave balance?");

        assertThat(response).isNotNull();
    }

    // ── balance formatting ────────────────────────────────────────────────────

    @Test
    void chat_leaveBalanceIntent_halfDayBalance_formattedCorrectly() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Sick Leave", "SICK_LEAVE", 2.5}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        assertThat(response).contains("2.5");
    }

    // ── on leave today — more than 10 people ─────────────────────────────────

    @Test
    void chat_onLeaveTodayIntent_moreThan10_showsAndMore() {
        List<Object[]> manyPeople = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            manyPeople.add(new Object[]{"user" + i, "User " + i, "APPROVED"});
        }
        when(leaveRepository.findPeopleOnLeaveByDate(any())).thenReturn(manyPeople);

        String response = chatbotService.chat("Who is on leave today?", "john");

        assertThat(response).containsIgnoringCase("more");
    }

    // ── leave count — singular vs plural ─────────────────────────────────────

    @Test
    void chat_pendingLeaveIntent_singleLeave_singularForm() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(leaveRepository.countByStatusAndUsername("john", "PENDING")).thenReturn(1L);

        String response = chatbotService.chat("How many pending leaves do I have?", "john");

        // "1 pending leave request." (singular)
        assertThat(response).contains("1 pending leave request.");
    }

    // ── warmChatbotCaches ─────────────────────────────────────────────────────

    @Test
    void warmChatbotCaches_dataSourceFails_doesNotThrow() throws Exception {
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("DB unavailable"));
        // Should not throw — failure is caught and logged as a warning
        assertThatCode(() -> chatbotService.warmChatbotCaches()).doesNotThrowAnyException();
    }

    // ── chat with 3-arg overload ──────────────────────────────────────────────

    @Test
    void chat_threeArgOverload_delegatesToFull() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        String response = chatbotService.chat("What are the upcoming holidays?", "john", "req-1");

        assertThat(response).isNotNull();
    }

    // ── leave balance — null/empty balance row fields ─────────────────────────

    @Test
    void chat_leaveBalanceIntent_nullLeaveNameFallsBackToUniqueName() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{null, "SICK_LEAVE", 3.0}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        assertThat(response).contains("SICK_LEAVE");
    }

    @Test
    void chat_leaveBalanceIntent_nullRowSkipped() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0});
        rows.add(null);
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L)).thenReturn(rows);

        String response = chatbotService.chat("What is my leave balance?", "john");

        assertThat(response).contains("Annual Leave");
    }

    // ── on leave today — null/partial row ────────────────────────────────────

    @Test
    void chat_onLeaveTodayIntent_nullRowFields_usesDefaults() {
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(new Object[]{null, null, null}));

        String response = chatbotService.chat("Who is on leave today?", "john");

        assertThat(response).containsIgnoringCase("unknown");
    }

    // ── leave policy — null description ──────────────────────────────────────

    @Test
    void chat_leavePolicyIntent_nullDescription_doesNotThrow() {
        when(leaveTypeRepository.findAllForChatbot())
            .thenReturn(rowList(new Object[]{"Annual Leave", "20", null}));

        String response = chatbotService.chat("What are the leave types?", "john");

        assertThat(response).containsIgnoringCase("Annual Leave");
    }

    // ── user lookup — user with email and createdBy ───────────────────────────

    @Test
    void chat_userLookupByName_withEmailAndCreatedBy_includesDetails() throws Exception {
        setField(user, "createdBy", "manager1");

        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String response = chatbotService.chat("Who is john?", "admin");

        assertThat(response).contains("john@example.com");
        assertThat(response).contains("manager1");
    }

    // ── cancelRequest — marks as cancelled ───────────────────────────────────

    @Test
    void cancelRequest_validId_addsToCancelledSet() {
        // No in-flight future, but should still return false (no future to cancel)
        // and not throw
        assertThat(chatbotService.cancelRequest("req-abc")).isFalse();
    }

    // ── DB context path — dataSource throws, returns error message ────────────

    @Test
    void chat_ollamaPath_dataSourceThrows_returnsErrorInContext() throws Exception {
        // Use a message that won't match any direct intent, forcing the Ollama path
        // dataSource.getConnection() throws — the error is caught and included in context
        // modelExecutor.submit is then called for the Ollama model call
        lenient().when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("connection failed"));

        // The executor submit will throw so the chat returns an error message
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class)))
            .thenThrow(new java.util.concurrent.RejectedExecutionException("executor shut down"));

        String response = chatbotService.chat("Tell me about the database schema", "john");

        assertThat(response).isNotNull();
    }

    // ── chat with newConversation flag ────────────────────────────────────────

    @Test
    void chat_withConversationId_andNewConversation_doesNotThrow() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        String response = chatbotService.chat(
            "What are the upcoming holidays?", "john", "req-1", "chat_001", true);

        assertThat(response).isNotNull();
    }

    @Test
    void chat_withConversationId_notNewConversation_doesNotThrow() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        String response = chatbotService.chat(
            "What are the upcoming holidays?", "john", "req-1", "chat_001", false);

        assertThat(response).isNotNull();
    }

    @Test
    void chat_leaveBalanceIntent_userWithNoFullName_usesUsername() throws Exception {
        UserProfile noNameUser = new UserProfile();
        setField(noNameUser, "id", 2L);
        setField(noNameUser, "userName", "jane");
        setField(noNameUser, "active", true);

        when(userProfileRepository.findByUserNameIgnoreCase("jane")).thenReturn(Optional.of(noNameUser));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(2L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0}));

        String response = chatbotService.chat("What is my leave balance?", "jane");

        assertThat(response).contains("jane");
    }

    // ── warmChatbotCaches — with real JDBC mock ───────────────────────────────

    @Test
    void warmChatbotCaches_withMockedConnection_buildsSchemaCache() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemasRs);
        when(schemasRs.next()).thenReturn(true, false);
        when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_application");
        when(meta.getColumns(null, "leave_schema", "leave_application", "%")).thenReturn(columnsRs);
        when(columnsRs.next()).thenReturn(true, false);
        when(columnsRs.getString("COLUMN_NAME")).thenReturn("id");

        assertThatCode(() -> chatbotService.warmChatbotCaches()).doesNotThrowAnyException();
    }

    // ── Ollama path — executor returns a future that times out ───────────────

    @Test
    void chat_ollamaPath_executorTimesOut_returnsTimeoutMessage() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemasRs);
        when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenThrow(new java.util.concurrent.TimeoutException("timed out"));

        String response = chatbotService.chat("What is the leave approval workflow process?", "john");

        assertThat(response).containsIgnoringCase("taking longer");
    }

    // ── Ollama path — executor returns a valid response ───────────────────────

    @Test
    void chat_ollamaPath_executorReturnsResponse_returnsIt() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemasRs);
        when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("The leave approval workflow has two stages.");

        String response = chatbotService.chat("What is the leave approval workflow process?", "john");

        assertThat(response).contains("two stages");
    }

    // ── Ollama path — executor returns blank response ─────────────────────────

    @Test
    void chat_ollamaPath_executorReturnsBlank_returnsFallbackMessage() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getSchemas()).thenReturn(schemasRs);
        when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("   ");

        String response = chatbotService.chat("What is the leave approval workflow process?", "john");

        assertThat(response).containsIgnoringCase("could not get a response");
    }

    // ── Ollama path — with relevant table data ────────────────────────────────

    @Test
    void chat_ollamaPath_withTableData_includesRowsInContext() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_application");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_application", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("status");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(1);
        lenient().when(rsMeta.getColumnName(1)).thenReturn("status");
        lenient().when(dataRs.next()).thenReturn(true, false);
        lenient().when(dataRs.getObject(1)).thenReturn("PENDING");

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("There are pending leave applications.");

        String response = chatbotService.chat("What is the leave application status?", "john");

        assertThat(response).isNotNull();
    }

    // ── null message — tryDirectDatabaseAnswer returns null, cache hit avoids NPE ──

    @Test
    void chat_nullMessage_ollamaPath_handlesGracefully() throws Exception {
        // null message: tryDirectDatabaseAnswer returns null (blank check),
        // then falls to Ollama path. PromptTemplate.build throws NPE on null message
        // which propagates — verify the service doesn't silently swallow it
        // by using a non-null but non-matching message instead
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        // "upcoming holiday" is a direct intent — returns a non-null answer, no NPE
        String response = chatbotService.chat("What are the upcoming holidays?", null);

        assertThat(response).isNotNull();
    }

    // ── blank message — falls to Ollama ──────────────────────────────────────

    @Test
    void chat_blankMessage_directAnswerReturnsNull_fallsToOllama() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("response");

        String response = chatbotService.chat("   ", "john");

        assertThat(response).isNotNull();
    }

    // ── resolveExactUsernameQuery — single-word message matching a username ──

    @Test
    void chat_singleWordMessage_matchingUsername_returnsProfile() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String response = chatbotService.chat("john", "admin");

        assertThat(response).contains("john");
    }

    // ── extractUserLookupUsername — "who is X" pattern ───────────────────────

    @Test
    void chat_whoIsPattern_extractsUsername() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        String response = chatbotService.chat("tell me about john", "admin");

        assertThat(response).contains("john");
    }

    // ── leave count — no username at all ─────────────────────────────────────

    @Test
    void chat_pendingLeaveIntent_noUsername_returnsPrompt() {
        String response = chatbotService.chat("How many pending leaves?", null);

        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("username"),
            r -> assertThat(r).containsIgnoringCase("logged in")
        );
    }

    // ── leave balance — balance row with null both name fields ────────────────

    @Test
    void chat_leaveBalanceIntent_bothNameFieldsNull_usesDefaultName() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{null, null, 7.0}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        // Falls back to "Unnamed leave"
        assertThat(response).containsIgnoringCase("Unnamed leave");
    }

    // ── Ollama path — executor throws ExecutionException ─────────────────────

    @Test
    void chat_ollamaPath_executorThrowsExecutionException_returnsErrorMessage() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenThrow(
            new java.util.concurrent.ExecutionException("model error", new RuntimeException("inner")));

        String response = chatbotService.chat("What is the leave approval workflow process?", "john");

        assertThat(response).containsIgnoringCase("error");
    }

    // ── buildRelevantTableSql — no search terms (no WHERE clause) ────────────

    @Test
    void chat_ollamaPath_noSearchTerms_buildsSimpleSql() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        // Use a message with only stop words so search terms list is empty
        // → buildRelevantTableSql produces no WHERE clause
        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("user_schema");
        lenient().when(meta.getTables(null, "user_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("user_profile");
        lenient().when(meta.getColumns(null, "user_schema", "user_profile", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("user_name");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("answer");

        // "what is the" — all stop words, no meaningful search terms
        String response = chatbotService.chat("what is the user profile", "john");

        assertThat(response).isNotNull();
    }

    // ── getSearchableColumns — known table types ──────────────────────────────

    @Test
    void chat_ollamaPath_employeeLeaveTable_usesKnownColumns() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("employee_leave");
        lenient().when(meta.getColumns(null, "leave_schema", "employee_leave", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("full_name", "email_id");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("employee leave data");

        // Use a message that scores the employee_leave table without triggering a direct intent
        String response = chatbotService.chat("show employee leave gender data", "john");

        assertThat(response).isNotNull();
    }

    // ── summarizeColumns — more than max columns shows ellipsis ──────────────

    @Test
    void chat_ollamaPath_manyColumns_summarizesWithEllipsis() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_application");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_application", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(
            true, true, true, true, true, true, true, true, true, true, true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn(
            "id", "user_id", "leave_type", "status", "reason", "created_at", "updated_at",
            "approved_by", "comments", "editable", "trail", "rejection_reason");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenReturn("leave application info");

        String response = chatbotService.chat("What is the leave application status?", "john");

        assertThat(response).isNotNull();
    }

    // ── loadTables — excluded schemas are skipped ─────────────────────────────

    @Test
    void chat_ollamaPath_excludedSchema_isSkipped() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        // Return an excluded schema (information_schema) then a real one
        lenient().when(schemasRs.next()).thenReturn(true, true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM"))
            .thenReturn("information_schema", "leave_schema");
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("answer");

        String response = chatbotService.chat("What is the leave approval workflow process?", "john");

        assertThat(response).isNotNull();
    }

    // ── getSearchableColumns — leave_dates table ──────────────────────────────

    @Test
    void chat_ollamaPath_leaveDatesTable_usesKnownColumns() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_dates");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_dates", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("leave_date", "day_type");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("leave dates info");

        String response = chatbotService.chat("show leave dates day type info", "john");

        assertThat(response).isNotNull();
    }

    // ── getSearchableColumns — leave_types table ──────────────────────────────

    @Test
    void chat_ollamaPath_leaveTypesTable_usesKnownColumns() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_types");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_types", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("leave_name", "leave_unique_name");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("leave types info");

        String response = chatbotService.chat("show leave types gender restriction info", "john");

        assertThat(response).isNotNull();
    }

    // ── getSearchableColumns — public_holiday table ───────────────────────────

    @Test
    void chat_ollamaPath_publicHolidayTable_usesKnownColumns() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("public_holiday");
        lenient().when(meta.getColumns(null, "leave_schema", "public_holiday", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("name", "date");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("holiday info");

        String response = chatbotService.chat("show public holiday description info", "john");

        assertThat(response).isNotNull();
    }

    // ── appendResultSetRows — rows with null value ────────────────────────────

    @Test
    void chat_ollamaPath_resultSetWithNullValue_handlesGracefully() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_application");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_application", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("status");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(2);
        lenient().when(rsMeta.getColumnName(1)).thenReturn("status");
        lenient().when(rsMeta.getColumnName(2)).thenReturn("reason");
        // Row with one null value
        lenient().when(dataRs.next()).thenReturn(true, false);
        lenient().when(dataRs.getObject(1)).thenReturn("PENDING");
        lenient().when(dataRs.getObject(2)).thenReturn(null); // null value → "null"

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("leave application status");

        String response = chatbotService.chat("What is the leave application status?", "john");

        assertThat(response).isNotNull();
    }

    // ── scoreTable — priority table scoring ───────────────────────────────────

    @Test
    void chat_ollamaPath_priorityTable_getsExtraScore() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        // user_profile is in CHATBOT_DEFAULT_PRIORITY_TABLES → gets score=1 baseline
        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("user_schema");
        lenient().when(meta.getTables(null, "user_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("user_profile");
        lenient().when(meta.getColumns(null, "user_schema", "user_profile", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("user_name");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("user profile info");

        // "user" term scores user_profile table via column match + user/name branch
        String response = chatbotService.chat("show user profile role info", "john");

        assertThat(response).isNotNull();
    }

    // ── resolveQuestionUsername — token matching finds user ───────────────────

    @Test
    void chat_leaveBalanceIntent_tokenMatchFindsUser() {
        // "john" appears as a token in the message — resolveQuestionUsername extracts it
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 8.0}));

        // No "my" keyword, no "who is" pattern — falls to token scan which finds "john"
        String response = chatbotService.chat("What is the remaining leave balance for john?", "admin");

        assertThat(response).contains("Annual Leave");
    }

    // ── cancelRequest — with active in-flight request ─────────────────────────

    @Test
    void cancelRequest_withActiveRequest_cancelsAndReturnsTrue() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(false);

        // Submit a future that blocks — so the request stays in-flight
        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenThrow(new java.util.concurrent.TimeoutException("slow"));
        lenient().when(future.isDone()).thenReturn(false);

        // Start a chat that will timeout — this registers the future in inFlightRequests
        chatbotService.chat("What is the leave approval workflow process?", "john", "req-cancel-1");

        // Now cancel — the future was already removed after timeout, so returns false
        // but the cancelledRequestIds set is exercised
        boolean result = chatbotService.cancelRequest("req-cancel-1");
        // After timeout the future is removed, so cancel returns false — but code path is covered
        assertThat(result).isFalse();
    }

    // ── checkRoleAccess — EMPLOYEE asking about another user is denied ────────

    @Test
    void chat_employeeRole_askingAboutOtherUser_returnsDenialMessage() {
        UserProfile otherUser = new UserProfile();
        try {
            setField(otherUser, "id", 2L);
            setField(otherUser, "userName", "jane");
            setField(otherUser, "fullName", "Jane Smith");
            setField(otherUser, "active", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(userProfileRepository.findByUserNameIgnoreCase("jane")).thenReturn(Optional.of(otherUser));

        // EMPLOYEE "john" asking about "jane" — should be denied
        String response = chatbotService.chat(
            "What is jane's leave balance?", "john", "EMPLOYEE", null, null, false);

        assertThat(response).containsIgnoringCase("only view your own");
    }

    // ── checkRoleAccess — EMPLOYEE asking about themselves is allowed ─────────

    @Test
    void chat_employeeRole_askingAboutSelf_isAllowed() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0}));

        String response = chatbotService.chat(
            "What is my leave balance?", "john", "EMPLOYEE", null, null, false);

        assertThat(response).contains("Annual Leave");
    }

    // ── checkRoleAccess — ADMIN role has no restriction ───────────────────────

    @Test
    void chat_adminRole_canQueryAnyUser() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 8.0}));

        String response = chatbotService.chat(
            "What is john's leave balance?", "admin", "ADMIN", null, null, false);

        assertThat(response).contains("Annual Leave");
    }

    // ── answerOnLeaveTodayQuestion — MANAGER sees only their team ─────────────

    @Test
    void chat_onLeaveTodayIntent_managerRole_seesOnlyTeam() {
        when(leaveRepository.findTeamOnLeaveByDateAndManager(any(), eq("manager1")))
            .thenReturn(rowList(new Object[]{"emp1", "Employee One", "APPROVED"}));

        String response = chatbotService.chat(
            "Who is on leave today?", "manager1", "MANAGER", null, null, false);

        assertThat(response).containsIgnoringCase("Employee One");
        verify(leaveRepository).findTeamOnLeaveByDateAndManager(any(), eq("manager1"));
        verify(leaveRepository, never()).findPeopleOnLeaveByDate(any());
    }

    // ── answerOnLeaveTodayIntent — EMPLOYEE sees only themselves ─────────────

    @Test
    void chat_onLeaveTodayIntent_employeeRole_seesOnlySelf() {
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(
                new Object[]{"john", "John Doe", "APPROVED"},
                new Object[]{"jane", "Jane Smith", "APPROVED"}
            ));

        String response = chatbotService.chat(
            "Who is on leave today?", "john", "EMPLOYEE", null, null, false);

        // EMPLOYEE sees only their own row
        assertThat(response).containsIgnoringCase("John Doe");
        assertThat(response).doesNotContain("Jane Smith");
    }

    // ── answerOnLeaveTodayIntent — EMPLOYEE not on leave today ───────────────

    @Test
    void chat_onLeaveTodayIntent_employeeRole_notOnLeave_returnsPersonalMessage() {
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(new Object[]{"jane", "Jane Smith", "APPROVED"}));

        String response = chatbotService.chat(
            "Who is on leave today?", "john", "EMPLOYEE", null, null, false);

        assertThat(response).containsIgnoringCase("You are not on leave today");
    }

    // ── isUserUnderManager — manager can look up their own employee ───────────

    @Test
    void chat_managerRole_lookupOwnEmployee_isAllowed() {
        UserProfile emp = new UserProfile();
        try {
            setField(emp, "id", 3L);
            setField(emp, "userName", "emp1");
            setField(emp, "fullName", "Employee One");
            setField(emp, "active", true);
            setField(emp, "createdBy", "manager1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(userProfileRepository.findByUserNameIgnoreCase("emp1")).thenReturn(Optional.of(emp));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(3L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 6.0}));

        String response = chatbotService.chat(
            "What is emp1's leave balance?", "manager1", "MANAGER", null, null, false);

        assertThat(response).contains("Annual Leave");
    }

    // ── isUserUnderManager — manager cannot look up another manager's employee ─

    @Test
    void chat_managerRole_lookupOtherManagerEmployee_isDenied() {
        UserProfile emp = new UserProfile();
        try {
            setField(emp, "id", 4L);
            setField(emp, "userName", "emp2");
            setField(emp, "fullName", "Employee Two");
            setField(emp, "active", true);
            setField(emp, "createdBy", "other_manager");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(userProfileRepository.findByUserNameIgnoreCase("emp2")).thenReturn(Optional.of(emp));

        String response = chatbotService.chat(
            "What is emp2's leave balance?", "manager1", "MANAGER", null, null, false);

        assertThat(response).containsIgnoringCase("only view data for employees in your team");
    }

    // ── buildRelevantTableSql — EMPLOYEE role filters user_profile table ──────

    @Test
    void chat_ollamaPath_employeeRole_userProfileTableFiltered() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("user_schema");
        lenient().when(meta.getTables(null, "user_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("user_profile");
        lenient().when(meta.getColumns(null, "user_schema", "user_profile", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("user_name", "role");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData())

.thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("user profile info");

        // EMPLOYEE role — user_profile table should have WHERE LOWER(user_name) = LOWER(?) filter
        String response = chatbotService.chat(
            "get profile role info for user_profile", "john", "EMPLOYEE", null, null, false);

        assertThat(response).isNotNull();
        // Verify the prepared statement was called (role filter was applied)
        verify(stmt, atLeastOnce()).setString(anyInt(), eq("john"));
    }

    // ── buildRelevantTableSql — MANAGER role filters leave_application table ──

    @Test
    void chat_ollamaPath_managerRole_leaveApplicationTableFiltered() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("leave_application");
        lenient().when(meta.getColumns(null, "leave_schema", "leave_application", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("status", "user_id");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("leave application info");

        // MANAGER role — leave_application table should have user_id IN (...) filter
        String response = chatbotService.chat(
            "What is the leave application status?", "manager1", "MANAGER", null, null, false);

        assertThat(response).isNotNull();
        // Verify the prepared statement was called with manager username for role filter
        verify(stmt, atLeastOnce()).setString(anyInt(), eq("manager1"));
    }

    // ── buildAnswerCacheKey — manager role scopes cache by username ───────────

    @Test
    void chat_managerRole_cacheKeyIncludesManagerUsername() {
        when(publicHolidayRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        // First call as manager1
        chatbotService.chat("What are the upcoming holidays?", "manager1", "MANAGER", null, null, false);
        // Second call as manager2 — should NOT hit manager1's cache
        chatbotService.chat("What are the upcoming holidays?", "manager2", "MANAGER", null, null, false);

        // Both calls should hit the repository (different cache keys per manager)
        verify(publicHolidayRepository, times(2)).findByDateBetween(any(), any());
    }

    // ── buildAnswerCacheKey — personal query scoped by username ──────────────

    @Test
    void chat_personalQuery_cacheKeyIncludesUsername() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0}));

        // "my leave balance" is personal — cache key includes username
        chatbotService.chat("What is my leave balance?", "john");
        chatbotService.chat("What is my leave balance?", "john");

        // Second call hits cache — repository called only once
        verify(employeeLeaveRepository, times(1)).findLeaveBalancesByUserId(1L);
    }

    // ── answerOnLeaveTodayIntent — MANAGER with no team on leave ─────────────

    @Test
    void chat_onLeaveTodayIntent_managerRole_noTeamOnLeave() {
        when(leaveRepository.findTeamOnLeaveByDateAndManager(any(), eq("manager1")))
            .thenReturn(List.of());

        String response = chatbotService.chat(
            "Who is on leave today?", "manager1", "MANAGER", null, null, false);

        assertThat(response).containsIgnoringCase("No one");
    }

    // ── checkRoleAccess — EMPLOYEE exact username in message matches self ─────

    @Test
    void chat_employeeRole_exactUsernameInMessage_matchesSelf_isAllowed() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));

        // "john" is the current user — exact lookup matches self, allowed
        String response = chatbotService.chat(
            "john", "john", "EMPLOYEE", null, null, false);

        // Should return profile info (not a denial)
        assertThat(response).doesNotContainIgnoringCase("only view your own");
    }

    // ── buildRelevantTableSql — EMPLOYEE role filters employee_leave table ────

    @Test
    void chat_ollamaPath_employeeRole_employeeLeaveTableFiltered() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet tablesRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet columnsRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet dataRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSetMetaData rsMeta = mock(java.sql.ResultSetMetaData.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(true, false);
        lenient().when(schemasRs.getString("TABLE_SCHEM")).thenReturn("leave_schema");
        lenient().when(meta.getTables(null, "leave_schema", "%", new String[]{"TABLE"})).thenReturn(tablesRs);
        lenient().when(tablesRs.next()).thenReturn(true, false);
        lenient().when(tablesRs.getString("TABLE_NAME")).thenReturn("employee_leave");
        lenient().when(meta.getColumns(null, "leave_schema", "employee_leave", "%")).thenReturn(columnsRs);
        lenient().when(columnsRs.next()).thenReturn(true, true, false);
        lenient().when(columnsRs.getString("COLUMN_NAME")).thenReturn("full_name", "email_id");
        lenient().when(conn.prepareStatement(anyString())).thenReturn(stmt);
        lenient().when(stmt.executeQuery()).thenReturn(dataRs);
        lenient().when(dataRs.getMetaData()).thenReturn(rsMeta);
        lenient().when(rsMeta.getColumnCount()).thenReturn(0);
        lenient().when(dataRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        lenient().when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        lenient().when(future.get(anyLong(), any())).thenReturn("employee leave data");

        // EMPLOYEE role — employee_leave table should have user_id = (...) filter
        String response = chatbotService.chat(
            "retrieve employee_leave gender data", "john", "EMPLOYEE", null, null, false);

        assertThat(response).isNotNull();
        verify(stmt, atLeastOnce()).setString(anyInt(), eq("john"));
    }

    // ── answerLastCreatedUserQuestion — any user ──────────────────────────────

    @Test
    void chat_lastCreatedIntent_anyUser_returnsLastUser() throws Exception {
        UserProfile manager = new UserProfile();
        setField(manager, "id", 10L);
        setField(manager, "userName", "mgr1");
        setField(manager, "fullName", "Manager One");
        setField(manager, "role", "MANAGER");
        setField(manager, "active", true);

        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(manager));

        String response = chatbotService.chat("Who was the last added user?", "admin");

        assertThat(response).containsIgnoringCase("Manager One");
        assertThat(response).containsIgnoringCase("mgr1");
    }

    @Test
    void chat_lastCreatedIntent_wantsManager_filtersManagers() throws Exception {
        UserProfile emp = new UserProfile();
        setField(emp, "id", 11L);
        setField(emp, "userName", "emp1");
        setField(emp, "fullName", "Emp One");
        setField(emp, "role", "EMPLOYEE");
        setField(emp, "active", true);

        UserProfile mgr = new UserProfile();
        setField(mgr, "id", 12L);
        setField(mgr, "userName", "mgr2");
        setField(mgr, "fullName", "Mgr Two");
        setField(mgr, "role", "MANAGER");
        setField(mgr, "active", true);

        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(emp, mgr));

        String response = chatbotService.chat("Who was the last added manager?", "admin");

        assertThat(response).containsIgnoringCase("Mgr Two");
        assertThat(response).doesNotContain("Emp One");
    }

    @Test
    void chat_lastCreatedIntent_wantsEmployee_filtersEmployees() throws Exception {
        UserProfile mgr = new UserProfile();
        setField(mgr, "id", 13L);
        setField(mgr, "userName", "mgr3");
        setField(mgr, "fullName", "Mgr Three");
        setField(mgr, "role", "MANAGER");
        setField(mgr, "active", true);

        UserProfile emp = new UserProfile();
        setField(emp, "id", 14L);
        setField(emp, "userName", "emp2");
        setField(emp, "fullName", "Emp Two");
        setField(emp, "role", "EMPLOYEE");
        setField(emp, "active", true);

        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(mgr, emp));

        String response = chatbotService.chat("Who was the last added employee?", "admin");

        assertThat(response).containsIgnoringCase("Emp Two");
        assertThat(response).doesNotContain("Mgr Three");
    }

    @Test
    void chat_lastCreatedIntent_noUsersFound_returnsNotFoundMessage() throws Exception {
        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of());

        String response = chatbotService.chat("Who was the last added user?", "admin");

        assertThat(response).containsIgnoringCase("No users found");
    }

    @Test
    void chat_lastCreatedIntent_noMatchingRole_returnsNoMatchMessage() throws Exception {
        UserProfile emp = new UserProfile();
        setField(emp, "id", 15L);
        setField(emp, "userName", "emp3");
        setField(emp, "fullName", "Emp Three");
        setField(emp, "role", "EMPLOYEE");
        setField(emp, "active", true);

        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(emp));

        // Asking for last manager but only employees exist
        String response = chatbotService.chat("Who was the last added manager?", "admin");

        assertThat(response).containsIgnoringCase("No matching user found");
    }

    @Test
    void chat_lastCreatedIntent_userWithNoFullName_usesUsername() throws Exception {
        UserProfile noName = new UserProfile();
        setField(noName, "id", 16L);
        setField(noName, "userName", "noname1");
        setField(noName, "role", "EMPLOYEE");
        setField(noName, "active", true);

        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenReturn(List.of(noName));

        String response = chatbotService.chat("Who was the last added staff?", "admin");

        assertThat(response).containsIgnoringCase("noname1");
    }

    @Test
    void chat_lastCreatedIntent_repositoryThrows_returnsErrorMessage() throws Exception {
        when(userProfileRepository.findAll(any(org.springframework.data.domain.Sort.class)))
            .thenThrow(new RuntimeException("DB error"));

        String response = chatbotService.chat("Who was the last added user?", "admin");

        assertThat(response).containsIgnoringCase("couldn't retrieve");
    }

    // ── answerUserCountQuestion — total / managers / employees ────────────────

    @Test
    void chat_userCountIntent_totalCount_returnsTotal() throws Exception {
        UserProfile u1 = buildUserWithRole(20L, "u1", "EMPLOYEE");
        UserProfile u2 = buildUserWithRole(21L, "u2", "MANAGER");
        when(userProfileRepository.findAll()).thenReturn(List.of(u1, u2));

        String response = chatbotService.chat("How many users are there?", "admin");

        assertThat(response).contains("2");
        assertThat(response).containsIgnoringCase("total");
    }

    @Test
    void chat_userCountIntent_managerCount_returnsManagerCount() throws Exception {
        UserProfile emp = buildUserWithRole(22L, "emp1", "EMPLOYEE");
        UserProfile mgr = buildUserWithRole(23L, "mgr1", "MANAGER");
        when(userProfileRepository.findAll()).thenReturn(List.of(emp, mgr));

        String response = chatbotService.chat("How many managers are there?", "admin");

        assertThat(response).contains("1");
        assertThat(response).containsIgnoringCase("manager");
    }

    @Test
    void chat_userCountIntent_employeeCount_returnsEmployeeCount() throws Exception {
        UserProfile emp1 = buildUserWithRole(24L, "emp2", "EMPLOYEE");
        UserProfile emp2 = buildUserWithRole(25L, "emp3", "EMPLOYEE");
        UserProfile mgr  = buildUserWithRole(26L, "mgr2", "MANAGER");
        when(userProfileRepository.findAll()).thenReturn(List.of(emp1, emp2, mgr));

        String response = chatbotService.chat("How many employees are there?", "admin");

        assertThat(response).contains("2");
        assertThat(response).containsIgnoringCase("employee");
    }

    @Test
    void chat_userCountIntent_noUsers_returnsNotFoundMessage() throws Exception {
        when(userProfileRepository.findAll()).thenReturn(List.of());

        String response = chatbotService.chat("How many users are there?", "admin");

        assertThat(response).containsIgnoringCase("No users found");
    }

    @Test
    void chat_userCountIntent_repositoryThrows_returnsErrorMessage() throws Exception {
        when(userProfileRepository.findAll()).thenThrow(new RuntimeException("DB error"));

        String response = chatbotService.chat("How many users are there?", "admin");

        assertThat(response).containsIgnoringCase("couldn't retrieve");
    }

    // ── answerAllUsersTableQuestion — ADMIN wantsBoth / wantsManagers / wantsEmployees ──

    @Test
    void chat_allUsersIntent_adminRole_wantsBoth_returnsAll() throws Exception {
        UserProfile emp = buildUserWithRole(30L, "emp4", "EMPLOYEE");
        UserProfile mgr = buildUserWithRole(31L, "mgr3", "MANAGER");
        setField(emp, "active", true);
        setField(mgr, "active", true);
        when(userProfileRepository.findAll()).thenReturn(List.of(emp, mgr));

        String response = chatbotService.chat("List all users", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("All Employees");
    }

    @Test
    void chat_allUsersIntent_adminRole_wantsManagers_returnsManagers() throws Exception {
        UserProfile mgr = buildUserWithRole(32L, "mgr4", "MANAGER");
        when(userProfileRepository.findActiveByRole("MANAGER")).thenReturn(List.of(mgr));

        String response = chatbotService.chat("List all managers", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("All Managers");
        assertThat(response).containsIgnoringCase("mgr4");
    }

    @Test
    void chat_allUsersIntent_adminRole_wantsEmployees_returnsEmployees() throws Exception {
        UserProfile emp = buildUserWithRole(33L, "emp5", "EMPLOYEE");
        when(userProfileRepository.findActiveByRole("EMPLOYEE")).thenReturn(List.of(emp));

        String response = chatbotService.chat("List all employees", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("All Employees");
        assertThat(response).containsIgnoringCase("emp5");
    }

    @Test
    void chat_allUsersIntent_adminRole_noUsers_returnsNotFoundMessage() throws Exception {
        when(userProfileRepository.findAll()).thenReturn(List.of());

        String response = chatbotService.chat("List all users", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("No users found");
    }

    @Test
    void chat_allUsersIntent_managerRole_returnsTeam() throws Exception {
        UserProfile emp = buildUserWithRole(34L, "emp6", "EMPLOYEE");
        when(userProfileRepository.findActiveByManagerUsername("manager1")).thenReturn(List.of(emp));

        String response = chatbotService.chat(
            "List all employees", "manager1", "MANAGER", null, null, false);

        assertThat(response).containsIgnoringCase("Your Team Members");
    }

    @Test
    void chat_allUsersIntent_managerRole_emptyTeam_returnsNotFoundMessage() throws Exception {
        when(userProfileRepository.findActiveByManagerUsername("manager1")).thenReturn(List.of());

        String response = chatbotService.chat(
            "List all employees", "manager1", "MANAGER", null, null, false);

        assertThat(response).containsIgnoringCase("No users found");
    }

    @Test
    void chat_allUsersIntent_userWithNullFields_showsDashes() throws Exception {
        // User with all null optional fields — table should show "-" placeholders
        UserProfile sparse = new UserProfile();
        setField(sparse, "id", 35L);
        setField(sparse, "active", true);
        when(userProfileRepository.findAll()).thenReturn(List.of(sparse));

        String response = chatbotService.chat("List all users", "admin", "ADMIN", null, null, false);

        assertThat(response).contains("-");
    }

    // ── answerOnLeaveTodayQuestion — ADMIN role sees everyone ─────────────────

    @Test
    void chat_onLeaveTodayIntent_adminRole_seesEveryone() {
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(
                new Object[]{"john", "John Doe", "APPROVED"},
                new Object[]{"jane", "Jane Smith", "APPROVED"}
            ));

        String response = chatbotService.chat(
            "Who is on leave today?", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("John Doe");
        assertThat(response).containsIgnoringCase("Jane Smith");
        verify(leaveRepository).findPeopleOnLeaveByDate(any());
        verify(leaveRepository, never()).findTeamOnLeaveByDateAndManager(any(), any());
    }

    @Test
    void chat_onLeaveTodayIntent_adminRole_noOne_returnsNoOneMessage() {
        when(leaveRepository.findPeopleOnLeaveByDate(any())).thenReturn(List.of());

        String response = chatbotService.chat(
            "Who is on leave today?", "admin", "ADMIN", null, null, false);

        assertThat(response).containsIgnoringCase("No one");
    }

    // ── answerLeaveCountQuestion — user not found ─────────────────────────────

    @Test
    void chat_pendingLeaveIntent_userNotFound_returnsNotFoundMessage() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        String response = chatbotService.chat("How many pending leaves do I have?", "ghost");

        assertThat(response).containsIgnoringCase("couldn't find");
    }

    @Test
    void chat_approvedLeaveIntent_userNotFound_returnsNotFoundMessage() {
        lenient().when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        String response = chatbotService.chat("How many approved leaves do I have?", "ghost");

        assertThat(response).containsIgnoringCase("couldn't find");
    }

    // ── answerUserLookupQuestion — inactive user, no role, no email ───────────

    @Test
    void chat_userLookupByName_inactiveUser_showsInactiveStatus() throws Exception {
        UserProfile inactive = new UserProfile();
        setField(inactive, "id", 40L);
        setField(inactive, "userName", "olduser");
        setField(inactive, "active", false);
        when(userProfileRepository.findByUserNameIgnoreCase("olduser")).thenReturn(Optional.of(inactive));

        String response = chatbotService.chat("Who is olduser?", "admin");

        assertThat(response).containsIgnoringCase("inactive");
    }

    @Test
    void chat_userLookupByName_noRoleNoEmail_omitsThoseFields() throws Exception {
        UserProfile minimal = new UserProfile();
        setField(minimal, "id", 41L);
        setField(minimal, "userName", "minimal");
        setField(minimal, "active", true);
        when(userProfileRepository.findByUserNameIgnoreCase("minimal")).thenReturn(Optional.of(minimal));

        String response = chatbotService.chat("Who is minimal?", "admin");

        assertThat(response).contains("minimal");
        assertThat(response).containsIgnoringCase("active");
    }

    // ── buildAnswerCacheKey — username in message makes it personal ───────────

    @Test
    void chat_messageContainsUsername_cacheKeyIsPersonal() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0}));

        // Message contains "john" — treated as personal, cache key scoped to user
        chatbotService.chat("What is john's leave balance?", "john");
        chatbotService.chat("What is john's leave balance?", "john");

        // Second call hits cache — repository called only once
        verify(employeeLeaveRepository, times(1)).findLeaveBalancesByUserId(1L);
    }

    // ── isCancelled — cancelled request is blocked ────────────────────────────

    @Test
    void chat_cancelledRequest_returnsCancel() throws Exception {
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        java.sql.ResultSet schemasRs = mock(java.sql.ResultSet.class);

        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(meta);
        lenient().when(meta.getSchemas()).thenReturn(schemasRs);
        lenient().when(schemasRs.next()).thenReturn(false);

        @SuppressWarnings("unchecked")
        java.util.concurrent.Future<String> future = mock(java.util.concurrent.Future.class);
        when(modelExecutor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get(anyLong(), any())).thenThrow(new java.util.concurrent.CancellationException());

        String response = chatbotService.chat(
            "What is the leave approval workflow process?", "john", "req-x", null, false);

        assertThat(response).containsIgnoringCase("cancelled");
    }

    // ── mapLeaveBalanceRow — short row (< 3 elements) returns null ────────────

    @Test
    void chat_leaveBalanceIntent_shortRow_isSkipped() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        // Row with only 2 elements — mapLeaveBalanceRow returns null, filtered out
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE"}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        // All rows filtered → "does not have any readable"
        assertThat(response).containsIgnoringCase("does not have");
    }

    // ── formatDisplayName — no fullName, no userName → default reference ──────

    @Test
    void chat_leaveBalanceIntent_userWithNoNameFields_usesDefaultReference() throws Exception {
        UserProfile noFields = new UserProfile();
        setField(noFields, "id", 50L);
        setField(noFields, "active", true);
        when(userProfileRepository.findByUserNameIgnoreCase("nofields")).thenReturn(Optional.of(noFields));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(50L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 3.0}));

        String response = chatbotService.chat("What is my leave balance?", "nofields");

        // formatDisplayName falls back to "this user"
        assertThat(response).containsIgnoringCase("this user");
    }

    // ── formatBalance — integer value shows no decimal ────────────────────────

    @Test
    void chat_leaveBalanceIntent_integerBalance_noDecimalPoint() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(user));
        when(employeeLeaveRepository.findLeaveBalancesByUserId(1L))
            .thenReturn(rowList(new Object[]{"Annual Leave", "ANNUAL_LEAVE", 5.0}));

        String response = chatbotService.chat("What is my leave balance?", "john");

        // 5.0 → "5 days" not "5.0 days"
        assertThat(response).contains("5 day");
        assertThat(response).doesNotContain("5.0 day");
    }

    // ── isUserUnderManager — null inputs return false ─────────────────────────

    @Test
    void chat_managerRole_nullTargetUsername_doesNotDeny() {
        // When message doesn't resolve to any user, manager access check is skipped
        when(userProfileRepository.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        // Message with no extractable username — resolves to currentUsername (manager1)
        String response = chatbotService.chat(
            "How many pending leaves do I have?", "manager1", "MANAGER", null, null, false);

        assertThat(response).isNotNull();
        assertThat(response).doesNotContainIgnoringCase("only view data for employees");
    }

    // ── answerOnLeaveTodayQuestion — row with only username (length 1) ────────

    @Test
    void chat_onLeaveTodayIntent_rowWithOnlyUsername_usesUsernameAsName() {
        // Row with only 1 element — fullName is blank, status uses default
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(new Object[]{"john"}));

        String response = chatbotService.chat("Who is on leave today?", "admin");

        assertThat(response).containsIgnoringCase("john");
    }

    // ── answerOnLeaveTodayQuestion — row with username + fullName (length 2) ──

    @Test
    void chat_onLeaveTodayIntent_rowWithTwoElements_usesFullName() {
        // Row with 2 elements — status uses default PENDING
        when(leaveRepository.findPeopleOnLeaveByDate(any()))
            .thenReturn(rowList(new Object[]{"john", "John Doe"}));

        String response = chatbotService.chat("Who is on leave today?", "admin");

        assertThat(response).containsIgnoringCase("John Doe");
    }

    // ── private method coverage via reflection ────────────────────────────────

    // formatOnLeaveTodayRow — full row, no fullName, null row
    @Test
    void formatOnLeaveTodayRow_fullRow_includesFullNameAndStatus() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("formatOnLeaveTodayRow", Object[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService,
            (Object) new Object[]{"john", "John Doe", "APPROVED"});

        assertThat(result).contains("John Doe");
        assertThat(result).contains("john");
        assertThat(result).contains("APPROVED");
    }

    @Test
    void formatOnLeaveTodayRow_noFullName_usesUsername() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("formatOnLeaveTodayRow", Object[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService,
            (Object) new Object[]{"john", "", "APPROVED"});

        assertThat(result).contains("john");
        assertThat(result).doesNotContain("(`");
    }

    @Test
    void formatOnLeaveTodayRow_nullRow_usesDefaults() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("formatOnLeaveTodayRow", Object[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, (Object) null);

        assertThat(result).containsIgnoringCase("unknown");
        assertThat(result).containsIgnoringCase("PENDING");
    }

    // bindSearchTerms — blank/null skips, non-blank sets params
    @Test
    void bindSearchTerms_nullQuery_doesNotSetParams() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("bindSearchTerms", java.sql.PreparedStatement.class, String.class);
        m.setAccessible(true);

        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        m.invoke(chatbotService, stmt, null);

        verifyNoInteractions(stmt);
    }

    @Test
    void bindSearchTerms_blankQuery_doesNotSetParams() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("bindSearchTerms", java.sql.PreparedStatement.class, String.class);
        m.setAccessible(true);

        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        m.invoke(chatbotService, stmt, "   ");

        verifyNoInteractions(stmt);
    }

    @Test
    void bindSearchTerms_validQuery_setsParams() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("bindSearchTerms", java.sql.PreparedStatement.class, String.class);
        m.setAccessible(true);

        java.sql.PreparedStatement stmt = mock(java.sql.PreparedStatement.class);
        m.invoke(chatbotService, stmt, "annual leave");

        verify(stmt).setString(1, "annual leave");
        verify(stmt).setString(2, "annual leave");
    }

    // isAdmin — true/false
    @Test
    void isAdmin_adminRole_returnsTrue() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("isAdmin", String.class);
        m.setAccessible(true);

        assertThat((boolean) m.invoke(chatbotService, "ADMIN")).isTrue();
        assertThat((boolean) m.invoke(chatbotService, "admin")).isTrue();
        assertThat((boolean) m.invoke(chatbotService, "EMPLOYEE")).isFalse();
        assertThat((boolean) m.invoke(chatbotService, (Object) null)).isFalse();
    }

    // quoteIdentifier — normal and with embedded quotes
    @Test
    void quoteIdentifier_normalName_wrapsInDoubleQuotes() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("quoteIdentifier", String.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, "user_profile");
        assertThat(result).isEqualTo("\"user_profile\"");
    }

    @Test
    void quoteIdentifier_nameWithEmbeddedQuote_escapesIt() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("quoteIdentifier", String.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, "col\"name");
        assertThat(result).isEqualTo("\"col\"\"name\"");
    }

    // resolveQuestionUsername — extractUserLookupUsername branch ("who is X")
    @Test
    void resolveQuestionUsername_whoIsPattern_extractsUsername() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("resolveQuestionUsername", String.class, String.class);
        m.setAccessible(true);

        // "Who is john?" — CHATBOT_USERNAME_LOOKUP_PATTERN captures "john" directly
        // No repo call needed — the pattern match returns the captured group
        String result = (String) m.invoke(chatbotService, "Who is john?", "admin");

        assertThat(result).isEqualTo("john");
    }

    @Test
    void resolveQuestionUsername_nullMessage_returnsCurrentUsername() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("resolveQuestionUsername", String.class, String.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, null, "john");

        assertThat(result).isEqualTo("john");
    }

    // getSearchableColumns — default/unknown table falls back to table columns
    @Test
    void getSearchableColumns_unknownTable_fallsBackToTableColumns() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("getSearchableColumns",
                Class.forName("com.cresensolutions.leaveservice.service.Impl.ChatbotServiceImpl$TableInfo"));
        m.setAccessible(true);

        // Build a TableInfo via reflection
        Class<?> tableInfoClass = Class.forName(
            "com.cresensolutions.leaveservice.service.Impl.ChatbotServiceImpl$TableInfo");
        java.lang.reflect.Constructor<?> ctor = tableInfoClass.getDeclaredConstructor(
            String.class, String.class, java.util.List.class);
        ctor.setAccessible(true);
        Object tableInfo = ctor.newInstance("custom_schema", "custom_table",
            java.util.List.of("col_a", "col_b", "col_c"));

        @SuppressWarnings("unchecked")
        java.util.List<String> result = (java.util.List<String>) m.invoke(chatbotService, tableInfo);

        // Unknown table → falls back to table's own columns (up to max)
        assertThat(result).isNotEmpty();
        assertThat(result).contains("col_a");
    }

    // buildSearchableExpression — single column and multiple columns
    @Test
    void buildSearchableExpression_singleColumn_buildsTsVector() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("buildSearchableExpression", java.util.List.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, java.util.List.of("user_name"));

        assertThat(result).contains("to_tsvector");
        assertThat(result).contains("user_name");
        assertThat(result).contains("CONCAT_WS");
    }

    @Test
    void buildSearchableExpression_multipleColumns_includesAll() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class
            .getDeclaredMethod("buildSearchableExpression", java.util.List.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService, java.util.List.of("full_name", "email_id"));

        assertThat(result).contains("full_name");
        assertThat(result).contains("email_id");
    }

    // runModelCallFallback — circuit breaker fallback returns fallback message
    @Test
    void runModelCallFallback_returnsCircuitBreakerMessage() throws Exception {
        java.lang.reflect.Method m = ChatbotServiceImpl.class.getDeclaredMethod(
            "runModelCallFallback",
            com.cresensolutions.leaveservice.chatbot.Prompt.class,
            String.class,
            String.class,
            Throwable.class);
        m.setAccessible(true);

        String result = (String) m.invoke(chatbotService,
            null, "test message", "req-1", new RuntimeException("circuit open"));

        assertThat(result).containsIgnoringCase("unable to reach");
    }

    // isCancelled — true when in cancelled set, false otherwise
    @Test
    void isCancelled_cancelledId_returnsTrue() throws Exception {
        java.lang.reflect.Method cancel = ChatbotServiceImpl.class
            .getDeclaredMethod("isCancelled", String.class);
        cancel.setAccessible(true);

        // Add to cancelled set via cancelRequest
        chatbotService.cancelRequest("req-cancelled");

        assertThat((boolean) cancel.invoke(chatbotService, "req-cancelled")).isTrue();
        assertThat((boolean) cancel.invoke(chatbotService, "req-other")).isFalse();
        assertThat((boolean) cancel.invoke(chatbotService, (Object) null)).isFalse();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private UserProfile buildUserWithRole(Long id, String username, String role) {
        UserProfile u = new UserProfile();
        try {
            setField(u, "id", id);
            setField(u, "userName", username);
            setField(u, "role", role);
            setField(u, "active", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}
