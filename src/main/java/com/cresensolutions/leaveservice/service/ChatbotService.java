package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.chatbot.Prompt;
import com.cresensolutions.leaveservice.chatbot.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatbotService {

    private final DataSource dataSource;
    private final Map<String, CachedContext> contextCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MINUTES = 15; // Cache for 15 minutes

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash-lite}")
    private String geminiModel;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    private static final String GEMINI_BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GROQ_URL =
        "https://api.groq.com/openai/v1/chat/completions";

    private static final List<String> EXCLUDED_SCHEMAS =
        List.of("information_schema", "pg_catalog", "pg_toast");

    private static final int MAX_ROWS_PER_TABLE = 50;
    private static final int MAX_CONTEXT_CHARS = 12_000;

    private static class CachedContext {
        final String context;
        final LocalDateTime timestamp;
        
        CachedContext(String context) {
            this.context = context;
            this.timestamp = LocalDateTime.now();
        }
        
        boolean isExpired() {
            return LocalDateTime.now().isAfter(timestamp.plusMinutes(CACHE_DURATION_MINUTES));
        }
    }

    public ChatbotService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String chat(String userMessage) {
        String dbContext = getCachedDatabaseContext();
        Prompt prompt = PromptTemplate.build(dbContext, userMessage);

        try {
            return callGemini(prompt);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                log.warn("Gemini quota exceeded, falling back to Groq");
            } else {
                log.error("Gemini error: {}", e.getMessage());
            }
            return callGroq(prompt);
        } catch (Exception e) {
            log.error("Gemini failed, falling back to Groq: {}", e.getMessage());
            return callGroq(prompt);
        }
    }

    private String getCachedDatabaseContext() {
        String cacheKey = "db_context";
        CachedContext cached = contextCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached database context");
            return cached.context;
        }
        
        log.debug("Building fresh database context");
        String context = buildFullDatabaseContext();
        contextCache.put(cacheKey, new CachedContext(context));
        
        // Clean up expired cache entries
        contextCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        return context;
    }

    private String buildFullDatabaseContext() {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== DATABASE SNAPSHOT (").append(LocalDate.now()).append(") ===\n\n");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> schemas = getSchemas(meta);
            for (String schema : schemas) {
                if (ctx.length() >= MAX_CONTEXT_CHARS) break;
                List<String> tables = getTables(meta, schema);
                if (tables.isEmpty()) continue;
                ctx.append("--- SCHEMA: ").append(schema).append(" ---\n");
                for (String table : tables) {
                    if (ctx.length() >= MAX_CONTEXT_CHARS) {
                        ctx.append("\n[Context truncated to fit model limits]\n");
                        break;
                    }
                    ctx.append("\n[").append(schema).append(".").append(table).append("]\n");
                    appendTableData(conn, schema, table, ctx);
                }
                ctx.append("\n");
            }
        } catch (Exception e) {
            log.error("Failed to build DB context", e);
            ctx.append("Error reading database: ").append(e.getMessage());
        }
        // Final hard truncation
        if (ctx.length() > MAX_CONTEXT_CHARS) {
            return ctx.substring(0, MAX_CONTEXT_CHARS) + "\n[Context truncated]\n";
        }
        return ctx.toString();
    }

    private List<String> getSchemas(DatabaseMetaData meta) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                if (!EXCLUDED_SCHEMAS.contains(schema)) {
                    schemas.add(schema);
                }
            }
        }
        return schemas;
    }

    private List<String> getTables(DatabaseMetaData meta, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private void appendTableData(Connection conn, String schema, String table, StringBuilder ctx) {
        String sql = String.format("SELECT * FROM \"%s\".\"%s\" LIMIT %d", schema, table, MAX_ROWS_PER_TABLE);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData rsMeta = rs.getMetaData();
            int colCount = rsMeta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                columns.add(rsMeta.getColumnName(i));
            }
            ctx.append("Columns: ").append(String.join(", ", columns)).append("\n");
            int rowCount = 0;
            while (rs.next()) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    values.add(val == null ? "null" : val.toString());
                }
                ctx.append(String.join(" | ", values)).append("\n");
                rowCount++;
            }
            ctx.append("(").append(rowCount).append(" rows)\n");
        } catch (Exception e) {
            ctx.append("Error reading table: ").append(e.getMessage()).append("\n");
        }
    }

    @SuppressWarnings("unchecked")
    private String callGemini(Prompt prompt) {
        RestTemplate restTemplate = new RestTemplate();
        String url = GEMINI_BASE_URL + geminiModel + ":generateContent?key=" + geminiApiKey;

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt.toSingleString()))))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url, new HttpEntity<>(requestBody, headers), Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        }
        throw new RuntimeException("Empty response from Gemini");
    }

    @SuppressWarnings("unchecked")
    private String callGroq(Prompt prompt) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return "Sorry, the AI service is currently unavailable. Please try again later.";
        }
        try {
            RestTemplate restTemplate = new RestTemplate();

            List<Map<String, String>> messages = prompt.toChatMessages().stream()
                .map(msg -> Map.of("role", msg.role(), "content", msg.content()))
                .toList();

            Map<String, Object> requestBody = Map.of("model", groqModel, "messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                GROQ_URL, new HttpEntity<>(requestBody, headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) return (String) message.get("content");
                }
            }
            return "Sorry, I could not get a response from the AI.";
        } catch (Exception e) {
            log.error("Groq also failed: {}", e.getMessage());
            return "Sorry, an error occurred while processing your request: " + e.getMessage();
        }
    }
}
