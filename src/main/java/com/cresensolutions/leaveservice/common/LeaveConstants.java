package com.cresensolutions.leaveservice.common;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class LeaveConstants {

    private LeaveConstants() {}
    public static final String ROLE_ADMIN    = "ADMIN";
    public static final String ROLE_MANAGER  = "MANAGER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String STATUS_PENDING           = "PENDING";
    public static final String STATUS_MANAGER_APPROVED  = "MANAGER_APPROVED";
    public static final String STATUS_APPROVED          = "APPROVED";
    public static final String STATUS_REJECTED          = "REJECTED";
    public static final String DAY_TYPE_FULL           = "FULL";
    public static final String DAY_TYPE_MORNING_HALF   = "MORNING_HALF";
    public static final String DAY_TYPE_AFTERNOON_HALF = "AFTERNOON_HALF";
    public static final String GENDER_MALE   = "MALE";
    public static final String GENDER_FEMALE = "FEMALE";
    public static final String PROCESS_DEF_KEY = "leaveApprovalProcess_1";
    public static final String TMPL_PENDING_APPROVAL          = "PENDING_APPROVAL";
    public static final String TMPL_MANAGER_APPROVED_PENDING   = "MANAGER_APPROVED_PENDING";
    public static final String TMPL_LEAVE_APPROVED             = "LEAVE_APPROVED";
    public static final String TMPL_LEAVE_REJECTED             = "LEAVE_REJECTED";
    public static final String TMPL_REMINDER_4DAY    = "REMINDER_4DAY";
    public static final String TMPL_REMINDER_2DAY    = "REMINDER_2DAY";
    public static final String LOGO_CID = "cresenSolutionsLogo";
    public static final String REMINDER_4DAY = "4DAY";
    public static final String REMINDER_2DAY = "2DAY";
    public static final String EVENT_SUBMITTED         = "SUBMITTED";
    public static final String EVENT_APPROVER_RESOLVED = "APPROVER_RESOLVED";
    public static final String SYSTEM_ACTOR            = "system";
    public static final String SYSTEM_DISPLAY_NAME     = "System";
    public static final String DEFAULT_ADMIN_USERNAME  = "admin";
    public static final String DAY_TYPE_HALF_KEYWORD = "HALF";
    public static final String DEFAULT_EMPLOYEE_NAME = "Employee";
    public static final String CHATBOT_SOURCE_CACHE = "cache";
    public static final String CHATBOT_SOURCE_DIRECT_DB = "direct_db";
    public static final String CHATBOT_SOURCE_OLLAMA = "ollama";
    public static final String CHATBOT_SCHEMA_OVERVIEW_CACHE_KEY = "schema_overview";
    public static final String CHATBOT_REQUEST_MESSAGE_KEY = "message";
    public static final String CHATBOT_REQUEST_USERNAME_KEY = "username";
    public static final String CHATBOT_REQUEST_ID_KEY = "requestId";
    public static final String CHATBOT_CONVERSATION_ID_KEY = "conversationId";
    public static final String CHATBOT_NEW_CONVERSATION_KEY = "newConversation";
    public static final String CHATBOT_CANCELLED_KEY = "cancelled";
    public static final String CHATBOT_RESPONSE_KEY = "response";
    public static final String CHATBOT_ERROR_KEY = "error";
    public static final String CHATBOT_EMPTY_MESSAGE_ERROR = "Message cannot be empty";
    public static final String CHATBOT_CANCELLED_RESPONSE = "Chat request was cancelled.";
    public static final String CHATBOT_FTS_CONFIG = "simple";
    public static final String CHATBOT_STATUS_ACTIVE = "active";
    public static final String CHATBOT_STATUS_INACTIVE = "inactive";
    public static final String CHATBOT_DEFAULT_UNKNOWN_USERNAME = "unknown";
    public static final String CHATBOT_DEFAULT_PENDING_STATUS = STATUS_PENDING;
    public static final String CHATBOT_DEFAULT_UNNAMED_LEAVE = "Unnamed leave";
    public static final String CHATBOT_DEFAULT_USER_REFERENCE = "this user";
    public static final String CHATBOT_SESSION_DEFAULT_TITLE = "Chat session";
    public static final String CHATBOT_TITLE_ELLIPSIS = "...";
    public static final long CHATBOT_SESSION_GAP_MINUTES = 30;
    public static final int CHATBOT_SESSION_TITLE_MAX_LENGTH = 80;
    public static final int CHATBOT_MODEL_EXECUTOR_THREADS = 10;
    public static final long CHATBOT_SCHEMA_CACHE_MINUTES = 60;
    public static final long CHATBOT_CONTEXT_CACHE_MINUTES = 15;
    public static final long CHATBOT_ANSWER_CACHE_MINUTES = 10;
    public static final long CHATBOT_USER_LOOKUP_CACHE_MINUTES = 10;
    public static final int CHATBOT_ANSWER_CACHE_MAX_SIZE = 1000;
    public static final int CHATBOT_USER_LOOKUP_CACHE_MAX_SIZE = 500;
    public static final int CHATBOT_MAX_SCHEMA_TABLES = 24;
    public static final int CHATBOT_MAX_OVERVIEW_COLUMNS_PER_TABLE = 8;
    public static final int CHATBOT_MAX_RELEVANT_TABLES = 4;
    public static final int CHATBOT_MAX_ROWS_PER_TABLE = 4;
    public static final int CHATBOT_MAX_CONTEXT_CHARS = 4_000;
    public static final int CHATBOT_MAX_SEARCH_COLUMNS_PER_TABLE = 4;
    public static final List<String> CHATBOT_EXCLUDED_SCHEMAS = List.of(
        "information_schema", "pg_catalog", "pg_toast", "flowable", "public"
    );
    public static final List<String> CHATBOT_USER_PROFILE_SEARCH_COLUMNS = List.of(
        "user_name", "full_name", "email_id", "role"
    );
    public static final List<String> CHATBOT_LEAVE_APPLICATION_SEARCH_COLUMNS = List.of(
        "user_id", "leave_type", "status", "approved_by"
    );
    public static final List<String> CHATBOT_EMPLOYEE_LEAVE_SEARCH_COLUMNS = List.of(
        "full_name", "email_id", "gender", "leaves"
    );
    public static final List<String> CHATBOT_LEAVE_DATES_SEARCH_COLUMNS = List.of(
        "leave_application_id", "leave_date", "day_type"
    );
    public static final List<String> CHATBOT_LEAVE_TYPES_SEARCH_COLUMNS = List.of(
        "leave_name", "leave_unique_name", "description", "gender_restriction"
    );
    public static final List<String> CHATBOT_HOLIDAY_SEARCH_COLUMNS = List.of(
        "name", "date", "description"
    );
    public static final Pattern CHATBOT_USERNAME_LOOKUP_PATTERN = Pattern.compile(
        "\\b(?:who\\s+is|who's|find|tell\\s+me\\s+about)\\s+([A-Za-z0-9._-]+)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_USERNAME_TOKEN_PATTERN =
        Pattern.compile("\\b[A-Za-z][A-Za-z0-9._-]*\\b");
    public static final Pattern CHATBOT_TERM_PATTERN =
        Pattern.compile("[A-Za-z0-9._-]{3,}");
    public static final Pattern CHATBOT_LEAVE_BALANCE_INTENT_PATTERN = Pattern.compile(
        "\\b(?:leave\\s+balance|remaining\\s+leave|remaining\\s+balance)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_PENDING_LEAVE_INTENT_PATTERN = Pattern.compile(
        "\\b(?:pending\\s+leave|pending\\s+leaves)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_APPROVED_LEAVE_INTENT_PATTERN = Pattern.compile(
        "\\b(?:approved\\s+leave|approved\\s+leaves|leaves\\s+approved|leave\\s+approved)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_REJECTED_LEAVE_INTENT_PATTERN = Pattern.compile(
        "\\b(?:rejected\\s+leave|rejected\\s+leaves|leaves\\s+rejected|leave\\s+rejected|how\\s+many\\s+rejected)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_UPCOMING_HOLIDAY_INTENT_PATTERN = Pattern.compile(
        "\\b(?:upcoming\\s+holiday|upcoming\\s+holidays|next\\s+holiday)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_ON_LEAVE_TODAY_INTENT_PATTERN = Pattern.compile(
        "\\b(?:on\\s+leave\\s+today|who\\s+is\\s+on\\s+leave\\s+today)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_LEAVE_POLICY_INTENT_PATTERN = Pattern.compile(
        "\\b(?:leave\\s+polic\\w*|leave\\s+rule\\w*|leave\\s+type\\w*|types\\s+of\\s+leave|what\\s+leaves|available\\s+leave)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Pattern CHATBOT_SELF_SERVICE_INTENT_PATTERN = Pattern.compile(
        "\\b(?:my|what\\s+is\\s+my|what's\\s+my|current\\s+leave\\s+balance|how\\s+many\\s+(?:pending|approved)\\s+leaves?\\s+do\\s+i\\s+have|my\\s+(?:pending|approved)\\s+leaves?)\\b",
        Pattern.CASE_INSENSITIVE
    );
    public static final Set<String> CHATBOT_STOP_WORDS = Set.of(
        "what", "when", "where", "which", "who", "whose", "whom", "why", "how",
        "is", "are", "was", "were", "be", "been", "being", "do", "does", "did",
        "have", "has", "had", "can", "could", "should", "would", "will",
        "the", "there", "their", "this", "that", "these", "those", "and", "for",
        "with", "from", "about", "into", "your", "you", "my", "our", "his", "her",
        "its", "them", "they", "all", "any", "each", "every", "know", "available",
        "database", "schema", "schemas", "thing", "things", "chatbot", "please",
        "tell", "show", "give", "list", "count", "many", "much", "today", "current", "explain"
    );
    public static final List<String> CHATBOT_DEFAULT_PRIORITY_TABLES = List.of(
        "user_schema.user_profile",
        "leave_schema.leave_application",
        "leave_schema.employee_leave",
        "leave_schema.leave_dates",
        "leave_schema.leave_types",
        "email_schema.email_configuration",
        "email_schema.email_template"
    );
    public static final String CHATBOT_SYSTEM_TEMPLATE =
        "You are an intelligent assistant for a Leave Management System backed by PostgreSQL.\n"
        + "Today's date is: {today}\n\n"
        + "DATABASE SCHEMA STRUCTURE:\n"
        + "This system uses three PostgreSQL schemas:\n"
        + "  - user_schema  : user_profile, role, country, phone_code, otp\n"
        + "  - leave_schema : leave_application, leave_types, leave_dates, employee_leave, leave_notify_users, public_holiday\n"
        + "  - email_schema : email_template, email_configuration\n\n"
        + "KEY TABLES:\n"
        + "  user_schema.user_profile   : id, user_name, full_name, email_id, role, active, gender, created_by\n"
        + "  leave_schema.leave_types   : id, leave_name, leave_unique_name, description, max_days, gender_restriction\n"
        + "  leave_schema.employee_leave: id, user_id, full_name, email_id, leaves (JSONB with balances), gender\n"
        + "  leave_schema.leave_application: id, user_id, leave_type_id, leave_type, status, reason, approved_by, created_at\n"
        + "  leave_schema.leave_dates   : id, leave_application_id, leave_date, day_type\n\n"
        + "LEAVE BALANCE: stored as JSONB in employee_leave.leaves. Each key is a leave_unique_name (e.g. ANNUAL_LEAVE), "
        + "value contains allocated and used days. Remaining = allocated - used.\n\n"
        + "RULES:\n"
        + "- Answer ONLY based on the database data provided below.\n"
        + "- Use available tools for live user-specific facts: leave balance, pending/approved leave counts, user profile lookup.\n"
        + "- If the user asks about 'my' data and the current logged-in username is present in the context, use that username when calling tools.\n"
        + "- The context includes all known schemas and question-specific live rows pulled from the database.\n"
        + "- Always give specific answers with names, counts, statuses, and dates from the data.\n"
        + "- Never say you cannot determine the answer if the data is present in the provided context.\n"
        + "- For date-based questions (e.g. 'on leave today'), compare against today's date: {today}.\n"
        + "- Keep answers concise and factual. Do not make up data.\n\n"
        + "DATABASE CONTEXT (live data):\n"
        + "{dbContext}";
    public static final String CHATBOT_USER_TEMPLATE = "{question}";
}
