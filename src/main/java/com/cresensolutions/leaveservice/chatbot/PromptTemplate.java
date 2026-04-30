package com.cresensolutions.leaveservice.chatbot;

import com.cresensolutions.leaveservice.common.LeaveConstants;

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


    public static final PromptTemplate SYSTEM_TEMPLATE =
        new PromptTemplate(LeaveConstants.CHATBOT_SYSTEM_TEMPLATE);

   
    public static final PromptTemplate USER_TEMPLATE =
        new PromptTemplate(LeaveConstants.CHATBOT_USER_TEMPLATE);

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
