package com.cresensolutions.leaveservice.chatbot;

import java.time.LocalDate;
import java.util.Map;


public class PromptTemplate {

    private final String template;

    public PromptTemplate(String template) {
        this.template = template;
    }


    public String render(Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }


    public static final PromptTemplate SYSTEM_TEMPLATE = new PromptTemplate(
        "You are an intelligent assistant for a Leave Management System backed by PostgreSQL.\n"
        + "Today's date is: {today}\n\n"
        + "RULES:\n"
        + "- Answer ONLY based on the database data provided below.\n"
        + "- Always give specific answers with names, counts, and dates from the data.\n"
        + "- Never say you cannot determine the answer if the data is present.\n"
        + "- For date-based questions (e.g. 'on leave today'), compare against today's date: {today}.\n"
        + "- Keep answers concise and factual.\n\n"
        + "DATABASE SNAPSHOT:\n"
        + "{dbContext}"
    );

   
    public static final PromptTemplate USER_TEMPLATE = new PromptTemplate("{question}");

    public static Prompt build(String dbContext, String userQuestion) {
        String today = LocalDate.now().toString();

        SystemMessage system = SystemMessage.of(
            SYSTEM_TEMPLATE.render(Map.of(
                "today", today,
                "dbContext", dbContext
            ))
        );

        UserMessage user = UserMessage.of(
            USER_TEMPLATE.render(Map.of("question", userQuestion))
        );

        return Prompt.create(system, user);
    }
}
