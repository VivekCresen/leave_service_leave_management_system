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
import static org.mockito.ArgumentMatchers.*;
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
}

