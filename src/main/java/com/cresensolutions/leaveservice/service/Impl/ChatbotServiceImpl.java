package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.chatbot.ChatbotTools;
import com.cresensolutions.leaveservice.chatbot.Prompt;
import com.cresensolutions.leaveservice.chatbot.PromptTemplate;
import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.common.StringUtils;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveTypeRepository;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.ChatHistoryService;
import com.cresensolutions.leaveservice.service.ChatbotService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final DataSource dataSource;
    private final UserProfileRepository userProfileRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveRepository leaveRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final ChatHistoryService chatHistoryService;
    private final ChatClient chatClient;
    private final ExecutorService modelExecutor;
    private final long modelTimeoutSeconds;
    private final Cache<String, CachedAnswer> answerCache;
    private final Cache<String, String> schemaOverviewCache;
    private final Cache<String, Optional<UserProfile>> userProfileCache;
    private final Map<String, Future<String>> inFlightRequests = new ConcurrentHashMap<>();
    private final Set<String> cancelledRequestIds = ConcurrentHashMap.newKeySet();
    private final List<DirectIntent> directIntents;

    private record CachedAnswer(String answer, String source) {
    }

    private record TableInfo(String schema, String table, List<String> columns) {
        String qualifiedName() {
            return schema + "." + table;
        }
    }

    private record LeaveBalanceRow(String leaveName, double remainingBalance) {
    }

    private record DirectIntent(Pattern pattern, BiFunction<String, String, String> handler) {
        boolean matches(String normalizedMessage) {
            return pattern.matcher(normalizedMessage).find();
        }
    }

    public ChatbotServiceImpl(DataSource dataSource,
                              UserProfileRepository userProfileRepository,
                              EmployeeLeaveRepository employeeLeaveRepository,
                              LeaveRepository leaveRepository,
                              LeaveTypeRepository leaveTypeRepository,
                              PublicHolidayRepository publicHolidayRepository,
                              ChatHistoryService chatHistoryService,
                              ChatClient.Builder chatClientBuilder,
                              ChatbotTools chatbotTools,
                              @Qualifier("chatbotModelExecutor") ExecutorService modelExecutor,
                              @Value("${app.chatbot.model-timeout-seconds:20}") long modelTimeoutSeconds) {
        this.dataSource = dataSource;
        this.userProfileRepository = userProfileRepository;
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveRepository = leaveRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.publicHolidayRepository = publicHolidayRepository;
        this.chatHistoryService = chatHistoryService;
        this.modelExecutor = modelExecutor;
        this.modelTimeoutSeconds = modelTimeoutSeconds;
        this.chatClient = chatClientBuilder
            .defaultTools(chatbotTools)
            .build();
        this.answerCache = Caffeine.newBuilder()
            .expireAfterWrite(LeaveConstants.CHATBOT_ANSWER_CACHE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(LeaveConstants.CHATBOT_ANSWER_CACHE_MAX_SIZE)
            .build();
        this.schemaOverviewCache = Caffeine.newBuilder()
            .expireAfterWrite(LeaveConstants.CHATBOT_SCHEMA_CACHE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(1)
            .build();
        this.userProfileCache = Caffeine.newBuilder()
            .expireAfterWrite(LeaveConstants.CHATBOT_USER_LOOKUP_CACHE_MINUTES, TimeUnit.MINUTES)
            .maximumSize(LeaveConstants.CHATBOT_USER_LOOKUP_CACHE_MAX_SIZE)
            .build();
        this.directIntents = List.of(
            new DirectIntent(LeaveConstants.CHATBOT_LEAVE_BALANCE_INTENT_PATTERN, this::answerLeaveBalanceQuestion),
            new DirectIntent(LeaveConstants.CHATBOT_PENDING_LEAVE_INTENT_PATTERN,
                (msg, user) -> answerLeaveCountQuestion(msg, user, LeaveConstants.STATUS_PENDING, "pending")),
            new DirectIntent(LeaveConstants.CHATBOT_APPROVED_LEAVE_INTENT_PATTERN,
                (msg, user) -> answerLeaveCountQuestion(msg, user, LeaveConstants.STATUS_APPROVED, "approved")),
            new DirectIntent(LeaveConstants.CHATBOT_REJECTED_LEAVE_INTENT_PATTERN,
                (msg, user) -> answerLeaveCountQuestion(msg, user, LeaveConstants.STATUS_REJECTED, "rejected")),
            new DirectIntent(LeaveConstants.CHATBOT_UPCOMING_HOLIDAY_INTENT_PATTERN, (message, username) -> answerUpcomingHolidayQuestion()),
            new DirectIntent(LeaveConstants.CHATBOT_ON_LEAVE_TODAY_INTENT_PATTERN, (message, username) -> answerOnLeaveTodayQuestion()),
            new DirectIntent(LeaveConstants.CHATBOT_LEAVE_POLICY_INTENT_PATTERN,
                (message, username) -> answerLeavePolicyQuestion())
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmChatbotCaches() {
        try {
            getCachedSchemaOverview();
            if (log.isInfoEnabled()) {
                log.info("Chatbot schema cache warmed successfully");
            }
        } catch (Exception e) {
            log.warn("Failed to warm chatbot schema cache: {}", e.getMessage());
        }
    }

    @Override
    public String chat(String userMessage) {
        return chat(userMessage, null, null, null, false);
    }

    @Override
    public String chat(String userMessage, String currentUsername) {
        return chat(userMessage, currentUsername, null, null, false);
    }

    @Override
    public String chat(String userMessage, String currentUsername, String requestId) {
        return chat(userMessage, currentUsername, requestId, null, false);
    }

    @Override
    public String chat(String userMessage, String currentUsername, String requestId, String conversationId, boolean newConversation) {
        OffsetDateTime askedAt = OffsetDateTime.now();
        long startMs = System.currentTimeMillis();
        String normalizedRequestId = normalizeUsername(requestId);
        if (normalizedRequestId != null) {
            cancelledRequestIds.remove(normalizedRequestId);
        }

        String cacheKey = buildAnswerCacheKey(userMessage, currentUsername);
        CachedAnswer cachedAnswer = answerCache.getIfPresent(cacheKey);
        if (cachedAnswer != null) {
            if (log.isDebugEnabled()) {
                log.debug("Answer cache HIT key='{}' user='{}'", cacheKey, currentUsername);
            }
            saveChatHistorySafely(
                currentUsername,
                userMessage,
                cachedAnswer.answer(),
                LeaveConstants.CHATBOT_SOURCE_CACHE,
                askedAt,
                startMs,
                conversationId,
                newConversation
            );
            return cachedAnswer.answer();
        }

        String source = LeaveConstants.CHATBOT_SOURCE_DIRECT_DB;
        String answer;
        String directAnswer = tryDirectDatabaseAnswer(userMessage, currentUsername);
        if (directAnswer != null && !directAnswer.isBlank()) {
            answer = directAnswer;
        } else {
            source = LeaveConstants.CHATBOT_SOURCE_OLLAMA;
            String dbContext = getQuestionAwareDatabaseContext(userMessage, currentUsername);
            Prompt prompt = PromptTemplate.build(dbContext, userMessage);
            try {
                String response = runModelCall(prompt, userMessage, normalizedRequestId);
                answer = (response == null || response.isBlank())
                    ? "Sorry, I could not get a response from the local Ollama model."
                    : response.trim();
            } catch (CancellationException e) {
                answer = LeaveConstants.CHATBOT_CANCELLED_RESPONSE;
            } catch (TimeoutException e) {
                log.warn("Ollama chat timed out after {} seconds", modelTimeoutSeconds, e);
                answer = "Sorry, the local chatbot is taking longer than expected right now. Please try a simpler question or try again in a moment.";
            } catch (Exception e) {
                log.error("Ollama chat failed", e);
                answer = "Sorry, an error occurred while processing your request with the local Ollama model: " + e.getMessage();
            }
        }

        answerCache.put(cacheKey, new CachedAnswer(answer, source));
        if (log.isDebugEnabled()) {
            log.debug("Answer cache MISS - stored key='{}' user='{}'", cacheKey, currentUsername);
        }
        saveChatHistorySafely(currentUsername, userMessage, answer, source, askedAt, startMs, conversationId, newConversation);
        return answer;
    }

    @Override
    public boolean cancelRequest(String requestId) {
        String normalizedRequestId = normalizeUsername(requestId);
        if (normalizedRequestId == null) {
            return false;
        }

        cancelledRequestIds.add(normalizedRequestId);
        Future<String> future = inFlightRequests.remove(normalizedRequestId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            log.info("ChatbotService: cancelled request '{}'", normalizedRequestId);
            return true;
        }
        return false;
    }

    private void saveChatHistorySafely(String currentUsername,
                                       String userMessage,
                                       String answer,
                                       String source,
                                       OffsetDateTime askedAt,
                                       long startMs,
                                       String conversationId,
                                       boolean newConversation) {
        try {
            long latencyMs = System.currentTimeMillis() - startMs;
            chatHistoryService.addQaPair(
                currentUsername,
                conversationId,
                newConversation,
                new ChatHistoryService.QaPair(
                    userMessage,
                    answer,
                    source,
                    latencyMs,
                    askedAt,
                    OffsetDateTime.now()
                )
            );
        } catch (Exception e) {
            log.warn("Failed to save chat history for user '{}': {}", currentUsername, e.getMessage());
        }
    }

    private String buildAnswerCacheKey(String userMessage, String currentUsername) {
        if (userMessage == null) {
            return "null";
        }
        String normalized = userMessage.trim().toLowerCase(Locale.ROOT);
        boolean isPersonal = normalized.contains(" my ")
            || normalized.startsWith("my ")
            || (currentUsername != null
                && normalized.contains(currentUsername.trim().toLowerCase(Locale.ROOT)));
        if (isPersonal && currentUsername != null && !currentUsername.isBlank()) {
            return currentUsername.trim().toLowerCase(Locale.ROOT) + "::" + normalized;
        }
        return normalized;
    }

    private String runModelCall(Prompt prompt, String userMessage, String requestId)
        throws InterruptedException, ExecutionException, TimeoutException {
        Future<String> future = modelExecutor.submit(() -> {
            if (Thread.currentThread().isInterrupted() || isCancelled(requestId)) {
                throw new CancellationException("Chat request was cancelled before execution.");
            }
            String response = chatClient.prompt()
                .system(prompt.getSystemMessage().content())
                .user(userMessage == null ? "" : userMessage.trim())
                .call()
                .content();
            if (Thread.currentThread().isInterrupted() || isCancelled(requestId)) {
                throw new CancellationException("Chat request was cancelled during execution.");
            }
            return response;
        });

        if (requestId != null) {
            inFlightRequests.put(requestId, future);
        }

        try {
            return future.get(modelTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (CancellationException e) {
            future.cancel(true);
            throw e;
        } finally {
            if (requestId != null) {
                inFlightRequests.remove(requestId);
                cancelledRequestIds.remove(requestId);
            }
        }
    }

    private boolean isCancelled(String requestId) {
        return requestId != null && cancelledRequestIds.contains(requestId);
    }

    private String tryDirectDatabaseAnswer(String userMessage, String currentUsername) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        String normalizedMessage = userMessage.trim().toLowerCase(Locale.ROOT);
        for (DirectIntent intent : directIntents) {
            if (intent.matches(normalizedMessage)) {
                return intent.handler().apply(userMessage, currentUsername);
            }
        }

        String exactUsernameLookup = resolveExactUsernameQuery(userMessage);
        if (exactUsernameLookup != null) {
            return answerUserLookupQuestion(exactUsernameLookup);
        }

        Optional<String> usernameFromQuestion = extractUserLookupUsername(userMessage);
        if (usernameFromQuestion.isPresent()) {
            return answerUserLookupQuestion(usernameFromQuestion.get());
        }

        return null;
    }

    private String answerLeavePolicyQuestion() {
        List<Object[]> types = leaveTypeRepository.findAllForChatbot();
        if (types == null || types.isEmpty()) {
            return "No leave types are configured in the system yet.";
        }
        StringBuilder sb = new StringBuilder("Leave types available in this system:\n");
        for (Object[] row : types) {
            String name = row[0] != null ? row[0].toString() : "Unknown";
            String maxDays = row[1] != null ? row[1].toString() : "N/A";
            String desc = row[2] != null ? row[2].toString() : "";
            sb.append("- ").append(name).append(": up to ").append(maxDays).append(" days");
            if (!desc.isBlank()) {
                sb.append(" - ").append(desc);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String answerLeaveBalanceQuestion(String userMessage, String currentUsername) {
        String username = resolveQuestionUsername(userMessage, currentUsername);
        if (username == null || username.isBlank()) {
            return "Please mention the username, or ask while logged in so I can look up your leave balance.";
        }

        Optional<UserProfile> user = findUserProfile(username);
        if (user.isEmpty()) {
            return "I couldn't find a user named `" + username + "` in the database.";
        }

        List<Object[]> balances = employeeLeaveRepository.findLeaveBalancesByUserId(user.get().getId());
        if (balances.isEmpty()) {
            return formatDisplayName(user.get()) + " does not have any leave balance records yet.";
        }

        List<LeaveBalanceRow> rows = balances.stream()
            .map(this::mapLeaveBalanceRow)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(LeaveBalanceRow::leaveName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (rows.isEmpty()) {
            return formatDisplayName(user.get()) + " does not have any readable leave balance records yet.";
        }

        StringBuilder response = new StringBuilder();
        response.append("Current leave balance for ").append(formatDisplayName(user.get())).append(":\n");
        for (LeaveBalanceRow row : rows) {
            response.append("- ")
                .append(row.leaveName())
                .append(": ")
                .append(formatBalance(row.remainingBalance()))
                .append(" day");
            if (Math.abs(row.remainingBalance() - 1.0d) > 0.0001d) {
                response.append('s');
            }
            response.append('\n');
        }
        return response.toString().trim();
    }

    private String answerUserLookupQuestion(String username) {
        Optional<UserProfile> user = findUserProfile(username);
        if (user.isEmpty()) {
            return "I couldn't find `" + username + "` in the database.";
        }

        UserProfile profile = user.get();
        StringBuilder response = new StringBuilder();
        response.append("`").append(profile.getUserName()).append("` is ");
        response.append(formatDisplayName(profile));

        if (profile.getRole() != null && !profile.getRole().isBlank()) {
            response.append(", role: ").append(profile.getRole());
        }
        response.append(", status: ").append(profile.isActive()
            ? LeaveConstants.CHATBOT_STATUS_ACTIVE
            : LeaveConstants.CHATBOT_STATUS_INACTIVE);

        if (profile.getEmailId() != null && !profile.getEmailId().isBlank()) {
            response.append(", email: ").append(profile.getEmailId());
        }

        if (profile.getCreatedBy() != null && !profile.getCreatedBy().isBlank()) {
            response.append(", manager/created by: ").append(profile.getCreatedBy());
        }

        response.append('.');
        return response.toString();
    }

    private String answerLeaveCountQuestion(String userMessage, String currentUsername, String status, String label) {
        String username = resolveQuestionUsername(userMessage, currentUsername);
        if (username == null || username.isBlank()) {
            return "Please mention the username, or ask while logged in so I can check " + label + " leaves.";
        }
        Optional<UserProfile> user = findUserProfile(username);
        if (user.isEmpty()) {
            return "I couldn't find a user named `" + username + "` in the database.";
        }
        long count = leaveRepository.countByStatusAndUsername(user.get().getUserName(), status);
        String displayName = formatDisplayName(user.get());
        return displayName + " has " + count + " " + label + " leave request" + (count == 1L ? "." : "s.");
    }

    private String answerUpcomingHolidayQuestion() {
        LocalDate today = LocalDate.now();
        List<String> lines = publicHolidayRepository.findByDateBetween(today, today.plusMonths(6)).stream()
            .limit(5)
            .map(holiday -> "- " + holiday.getDate() + ": " + holiday.getName())
            .toList();

        if (lines.isEmpty()) {
            return "I couldn't find any upcoming holidays in the next 6 months.";
        }

        return "Upcoming holidays:\n" + String.join("\n", lines);
    }

    private String answerOnLeaveTodayQuestion() {
        LocalDate today = LocalDate.now();
        List<Object[]> peopleOnLeave = leaveRepository.findPeopleOnLeaveByDate(today);
        if (peopleOnLeave.isEmpty()) {
            return "No one is on leave today.";
        }

        List<String> lines = peopleOnLeave.stream()
            .limit(10)
            .map(this::formatOnLeaveTodayRow)
            .toList();

        String suffix = peopleOnLeave.size() > lines.size()
            ? "\nAnd " + (peopleOnLeave.size() - lines.size()) + " more."
            : "";
        return "People on leave today:\n" + String.join("\n", lines) + suffix;
    }

    private String formatOnLeaveTodayRow(Object[] row) {
        String username = row != null && row.length > 0 && row[0] != null
            ? row[0].toString()
            : LeaveConstants.CHATBOT_DEFAULT_UNKNOWN_USERNAME;
        String fullName = row != null && row.length > 1 && row[1] != null ? row[1].toString() : "";
        String status = row != null && row.length > 2 && row[2] != null
            ? row[2].toString()
            : LeaveConstants.CHATBOT_DEFAULT_PENDING_STATUS;
        String displayName = fullName.isBlank() ? username : fullName + " (`" + username + "`)";
        return "- " + displayName + " [" + status + "]";
    }

    private String resolveQuestionUsername(String userMessage, String currentUsername) {
        String normalizedMessage = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (LeaveConstants.CHATBOT_SELF_SERVICE_INTENT_PATTERN.matcher(normalizedMessage).find()) {
            return normalizeUsername(currentUsername);
        }

        Optional<String> extracted = extractUserLookupUsername(userMessage);
        if (extracted.isPresent()) {
            return extracted.get();
        }

        Matcher matcher = LeaveConstants.CHATBOT_USERNAME_TOKEN_PATTERN.matcher(userMessage == null ? "" : userMessage);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.equalsIgnoreCase("what")
                || candidate.equalsIgnoreCase("is")
                || candidate.equalsIgnoreCase("leave")
                || candidate.equalsIgnoreCase("balance")
                || candidate.equalsIgnoreCase("current")
                || candidate.equalsIgnoreCase("remaining")
                || candidate.equalsIgnoreCase("my")) {
                continue;
            }
            Optional<UserProfile> user = findUserProfile(candidate);
            if (user.isPresent()) {
                return user.get().getUserName();
            }
        }

        return normalizeUsername(currentUsername);
    }

    private String resolveExactUsernameQuery(String userMessage) {
        String normalized = normalizeUsername(userMessage);
        if (normalized == null || normalized.contains(" ")) {
            return null;
        }

        return findUserProfile(normalized)
            .map(UserProfile::getUserName)
            .orElse(null);
    }

    private Optional<String> extractUserLookupUsername(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = LeaveConstants.CHATBOT_USERNAME_LOOKUP_PATTERN.matcher(userMessage.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.ofNullable(normalizeUsername(matcher.group(1)));
    }

    private Optional<UserProfile> findUserProfile(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return Optional.empty();
        }
        return userProfileCache.get(normalized.toLowerCase(Locale.ROOT), key ->
            userProfileRepository.findByUserNameIgnoreCase(normalized)
        );
    }

    private String normalizeUsername(String username) {
        return StringUtils.trimOrNull(username);
    }

    private LeaveBalanceRow mapLeaveBalanceRow(Object[] row) {
        if (row == null || row.length < 3) {
            return null;
        }

        String leaveName = row[0] == null ? null : row[0].toString();
        String leaveUniqueName = row[1] == null ? null : row[1].toString();
        double remainingBalance = row[2] instanceof Number
            ? ((Number) row[2]).doubleValue()
            : Double.parseDouble(String.valueOf(row[2]));

        String displayName = (leaveName != null && !leaveName.isBlank()) ? leaveName : leaveUniqueName;
        if (displayName == null || displayName.isBlank()) {
            displayName = LeaveConstants.CHATBOT_DEFAULT_UNNAMED_LEAVE;
        }

        return new LeaveBalanceRow(displayName, remainingBalance);
    }

    private String formatDisplayName(UserProfile user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return user.getUserName();
        }
        return LeaveConstants.CHATBOT_DEFAULT_USER_REFERENCE;
    }

    private String formatBalance(double balance) {
        if (Math.abs(balance - Math.rint(balance)) < 0.0001d) {
            return String.valueOf((long) Math.rint(balance));
        }
        return String.format(Locale.ROOT, "%.1f", balance);
    }

    private String getQuestionAwareDatabaseContext(String userMessage, String currentUsername) {
        String schemaOverview = getCachedSchemaOverview();
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== LIVE DATABASE CONTEXT (").append(LocalDate.now()).append(") ===\n");
        ctx.append("Question: ").append(userMessage == null ? "" : userMessage.trim()).append("\n");
        if (currentUsername != null && !currentUsername.isBlank()) {
            ctx.append("Current logged-in username: ").append(currentUsername.trim()).append("\n");
        }
        ctx.append("\n=== SCHEMA OVERVIEW ===\n").append(schemaOverview).append("\n");

        List<String> searchTerms = extractSearchTerms(userMessage, currentUsername);
        String fullTextQuery = buildFullTextQuery(searchTerms);
        ctx.append("=== SEARCH TERMS ===\n");
        ctx.append(searchTerms.isEmpty() ? "[none]\n" : String.join(", ", searchTerms) + "\n");

        try (Connection conn = dataSource.getConnection()) {
            List<TableInfo> tables = loadTables(conn.getMetaData());
            List<TableInfo> relevantTables = chooseRelevantTables(tables, searchTerms);
            ctx.append("\n=== LIVE MATCHED DATA ===\n");
            for (TableInfo table : relevantTables) {
                if (ctx.length() >= LeaveConstants.CHATBOT_MAX_CONTEXT_CHARS) {
                    ctx.append("\n[Context truncated to fit model limits]\n");
                    break;
                }
                appendRelevantTableData(conn, table, fullTextQuery, ctx);
            }
        } catch (Exception e) {
            log.error("Failed to build question-aware DB context", e);
            ctx.append("\nError reading database: ").append(e.getMessage()).append("\n");
        }

        if (ctx.length() > LeaveConstants.CHATBOT_MAX_CONTEXT_CHARS) {
            return ctx.substring(0, LeaveConstants.CHATBOT_MAX_CONTEXT_CHARS) + "\n[Context truncated]\n";
        }
        return ctx.toString();
    }

    private String getCachedSchemaOverview() {
        String cacheKey = LeaveConstants.CHATBOT_SCHEMA_OVERVIEW_CACHE_KEY;
        String cached = schemaOverviewCache.getIfPresent(cacheKey);
        if (cached != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using cached schema overview");
            }
            return cached;
        }

        if (log.isDebugEnabled()) {
            log.debug("Building fresh schema overview");
        }
        String context = buildSchemaOverview();
        schemaOverviewCache.put(cacheKey, context);
        return context;
    }

    private String buildSchemaOverview() {
        StringBuilder ctx = new StringBuilder();
        try (Connection conn = dataSource.getConnection()) {
            List<TableInfo> tables = loadTables(conn.getMetaData());
            String currentSchema = null;
            int tableCount = 0;
            for (TableInfo table : tables) {
                if (tableCount >= LeaveConstants.CHATBOT_MAX_SCHEMA_TABLES) {
                    ctx.append("\n[Additional tables omitted from overview]\n");
                    break;
                }
                if (!table.schema().equals(currentSchema)) {
                    currentSchema = table.schema();
                    ctx.append("\n--- SCHEMA: ").append(currentSchema).append(" ---\n");
                }
                ctx.append("[")
                    .append(table.qualifiedName())
                    .append("] columns: ")
                    .append(summarizeColumns(table.columns()))
                    .append("\n");
                tableCount++;
            }
        } catch (Exception e) {
            log.error("Failed to build schema overview", e);
            ctx.append("Error reading database: ").append(e.getMessage());
        }
        return ctx.toString();
    }

    private List<TableInfo> loadTables(DatabaseMetaData meta) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        try (ResultSet schemas = meta.getSchemas()) {
            while (schemas.next()) {
                String schema = schemas.getString("TABLE_SCHEM");
                if (LeaveConstants.CHATBOT_EXCLUDED_SCHEMAS.contains(schema)) {
                    continue;
                }
                try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        tables.add(new TableInfo(schema, tableName, getColumns(meta, schema, tableName)));
                    }
                }
            }
        }
        return tables;
    }

    private List<String> getColumns(DatabaseMetaData meta, String schema, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, schema, table, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private String summarizeColumns(List<String> columns) {
        if (columns.isEmpty()) {
            return "[no columns]";
        }
        List<String> visibleColumns = columns.size() > LeaveConstants.CHATBOT_MAX_OVERVIEW_COLUMNS_PER_TABLE
            ? columns.subList(0, LeaveConstants.CHATBOT_MAX_OVERVIEW_COLUMNS_PER_TABLE)
            : columns;
        String summary = String.join(", ", visibleColumns);
        return columns.size() > LeaveConstants.CHATBOT_MAX_OVERVIEW_COLUMNS_PER_TABLE ? summary + ", ..." : summary;
    }

    private List<String> extractSearchTerms(String userMessage, String currentUsername) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = LeaveConstants.CHATBOT_TERM_PATTERN.matcher(userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group().toLowerCase(Locale.ROOT);
            if (!LeaveConstants.CHATBOT_STOP_WORDS.contains(term)) {
                terms.add(term);
            }
        }
        if (currentUsername != null && !currentUsername.isBlank()) {
            terms.add(currentUsername.trim().toLowerCase(Locale.ROOT));
        }
        extractUserLookupUsername(userMessage)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .ifPresent(terms::add);
        return new ArrayList<>(terms);
    }

    private List<TableInfo> chooseRelevantTables(List<TableInfo> tables, List<String> searchTerms) {
        return tables.stream()
            .filter(table -> scoreTable(table, searchTerms) > 0)
            .sorted((left, right) -> Integer.compare(scoreTable(right, searchTerms), scoreTable(left, searchTerms)))
            .limit(LeaveConstants.CHATBOT_MAX_RELEVANT_TABLES)
            .toList();
    }

    private int scoreTable(TableInfo table, List<String> searchTerms) {
        String qualified = table.qualifiedName().toLowerCase(Locale.ROOT);
        int score = LeaveConstants.CHATBOT_DEFAULT_PRIORITY_TABLES.contains(qualified) ? 1 : 0;
        for (String term : searchTerms) {
            if (qualified.contains(term)) {
                score += 8;
            }
            for (String column : getSearchableColumns(table)) {
                String lowerColumn = column.toLowerCase(Locale.ROOT);
                if (lowerColumn.contains(term)) {
                    score += 4;
                }
                if (term.contains("leave") && lowerColumn.contains("status")) {
                    score += 2;
                }
                if ((term.contains("user") || term.contains("name")) && lowerColumn.contains("user")) {
                    score += 2;
                }
            }
        }
        if (qualified.contains("leave_application")) {
            score += 2;
        }
        if (qualified.contains("user_profile")) {
            score += 2;
        }
        return score;
    }

    private void appendRelevantTableData(Connection conn, TableInfo table, String fullTextQuery, StringBuilder ctx) {
        String sql = buildRelevantTableSql(table, fullTextQuery);
        ctx.append("\n[").append(table.qualifiedName()).append("]\n");
        ctx.append("Columns: ").append(summarizeColumns(table.columns())).append("\n");
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindSearchTerms(stmt, fullTextQuery);
            try (ResultSet rs = stmt.executeQuery()) {
                appendResultSetRows(rs, ctx);
            }
        } catch (Exception e) {
            ctx.append("Error reading table: ").append(e.getMessage()).append("\n");
        }
    }

    private String buildRelevantTableSql(TableInfo table, String fullTextQuery) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
            .append(quoteIdentifier(table.schema()))
            .append('.')
            .append(quoteIdentifier(table.table()));

        List<String> searchableColumns = getSearchableColumns(table);
        if (fullTextQuery != null && !fullTextQuery.isBlank() && !searchableColumns.isEmpty()) {
            sql.append(" WHERE ")
                .append(buildSearchableExpression(searchableColumns))
                .append(" @@ websearch_to_tsquery('")
                .append(LeaveConstants.CHATBOT_FTS_CONFIG)
                .append("', ?)")
                .append(" ORDER BY ts_rank(")
                .append(buildSearchableExpression(searchableColumns))
                .append(", websearch_to_tsquery('")
                .append(LeaveConstants.CHATBOT_FTS_CONFIG)
                .append("', ?)) DESC");
        }
        sql.append(" LIMIT ").append(LeaveConstants.CHATBOT_MAX_ROWS_PER_TABLE);
        return sql.toString();
    }

    private String buildSearchableExpression(List<String> columns) {
        StringBuilder expression = new StringBuilder("to_tsvector('")
            .append(LeaveConstants.CHATBOT_FTS_CONFIG)
            .append("', CONCAT_WS(' '");
        for (String column : columns) {
            expression.append(", COALESCE(CAST(")
                .append(quoteIdentifier(column))
                .append(" AS TEXT), '')");
        }
        expression.append("))");
        return expression.toString();
    }

    private List<String> getSearchableColumns(TableInfo table) {
        String qualifiedName = table.qualifiedName().toLowerCase(Locale.ROOT);
        List<String> preferredColumns = switch (qualifiedName) {
            case "user_schema.user_profile" -> LeaveConstants.CHATBOT_USER_PROFILE_SEARCH_COLUMNS;
            case "leave_schema.leave_application" -> LeaveConstants.CHATBOT_LEAVE_APPLICATION_SEARCH_COLUMNS;
            case "leave_schema.employee_leave" -> LeaveConstants.CHATBOT_EMPLOYEE_LEAVE_SEARCH_COLUMNS;
            case "leave_schema.leave_dates" -> LeaveConstants.CHATBOT_LEAVE_DATES_SEARCH_COLUMNS;
            case "leave_schema.leave_types" -> LeaveConstants.CHATBOT_LEAVE_TYPES_SEARCH_COLUMNS;
            case "leave_schema.public_holiday" -> LeaveConstants.CHATBOT_HOLIDAY_SEARCH_COLUMNS;
            default -> List.of();
        };

        List<String> matchedColumns = preferredColumns.stream()
            .filter(preferred -> table.columns().stream().anyMatch(column -> column.equalsIgnoreCase(preferred)))
            .toList();
        if (!matchedColumns.isEmpty()) {
            return matchedColumns;
        }
        return table.columns().stream()
            .limit(LeaveConstants.CHATBOT_MAX_SEARCH_COLUMNS_PER_TABLE)
            .toList();
    }

    private String buildFullTextQuery(List<String> searchTerms) {
        if (searchTerms == null || searchTerms.isEmpty()) {
            return null;
        }
        return String.join(" ", searchTerms);
    }

    private void bindSearchTerms(PreparedStatement stmt, String fullTextQuery) throws SQLException {
        if (fullTextQuery == null || fullTextQuery.isBlank()) {
            return;
        }
        stmt.setString(1, fullTextQuery);
        stmt.setString(2, fullTextQuery);
    }

    private void appendResultSetRows(ResultSet rs, StringBuilder ctx) throws SQLException {
        ResultSetMetaData rsMeta = rs.getMetaData();
        int colCount = rsMeta.getColumnCount();
        int rowCount = 0;
        while (rs.next()) {
            List<String> values = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                String column = rsMeta.getColumnName(i);
                Object val = rs.getObject(i);
                values.add(column + "=" + (val == null ? "null" : val.toString()));
            }
            ctx.append("- ").append(String.join(", ", values)).append("\n");
            rowCount++;
        }
        if (rowCount == 0) {
            ctx.append("- No matching rows found.\n");
        } else {
            ctx.append('(').append(rowCount).append(" rows)\n");
        }
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
