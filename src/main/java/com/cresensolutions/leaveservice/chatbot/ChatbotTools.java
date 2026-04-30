package com.cresensolutions.leaveservice.chatbot;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.common.StringUtils;
import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ChatbotTools {

    private final UserProfileRepository userProfileRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveRepository leaveRepository;

    public ChatbotTools(UserProfileRepository userProfileRepository,
                        EmployeeLeaveRepository employeeLeaveRepository,
                        LeaveRepository leaveRepository) {
        this.userProfileRepository = userProfileRepository;
        this.employeeLeaveRepository = employeeLeaveRepository;
        this.leaveRepository = leaveRepository;
    }

    @Tool(description = "Get the current leave balances for a user by username. Use this when the user asks about leave balance or remaining leave.")
    public String getLeaveBalance(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return "Please provide the employee username to check leave balance.";
        }

        Optional<UserProfile> user = userProfileRepository.findByUserNameIgnoreCase(normalizedUsername);
        if (user.isEmpty()) {
            return "I couldn't find a user named `" + normalizedUsername + "` in the database.";
        }

        List<Object[]> balances = employeeLeaveRepository.findLeaveBalancesByUserId(user.get().getId());
        if (balances.isEmpty()) {
            return formatDisplayName(user.get()) + " does not have any leave balance records yet.";
        }

        List<LeaveBalanceRow> rows = balances.stream()
            .map(this::mapLeaveBalanceRow)
            .filter(Objects::nonNull)
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

    @Tool(description = "Get the number of pending leave requests for a user by username.")
    public String getPendingLeaveRequests(String username) {
        return getLeaveCountByStatus(username, LeaveConstants.STATUS_PENDING, "pending");
    }

    @Tool(description = "Get the number of approved leave requests for a user by username.")
    public String getApprovedLeaveRequests(String username) {
        return getLeaveCountByStatus(username, LeaveConstants.STATUS_APPROVED, "approved");
    }

    @Tool(description = "Get the number of rejected leave requests for a user by username.")
    public String getRejectedLeaveRequests(String username) {
        return getLeaveCountByStatus(username, LeaveConstants.STATUS_REJECTED, "rejected");
    }

    private String getLeaveCountByStatus(String username, String status, String label) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return "Please provide the employee username to check " + label + " leave requests.";
        }
        Optional<UserProfile> user = userProfileRepository.findByUserNameIgnoreCase(normalizedUsername);
        if (user.isEmpty()) {
            return "I couldn't find a user named `" + normalizedUsername + "` in the database.";
        }
        long count = leaveRepository.countByStatusAndUsername(user.get().getUserName(), status);
        String displayName = formatDisplayName(user.get());
        return displayName + " has " + count + " " + label + " leave request" + (count == 1L ? "." : "s.");
    }

    @Tool(description = "Find user profile details by username, including full name, role, active status, email, and manager if present.")
    public String getUserProfile(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return "Please provide the employee username to look up profile details.";
        }

        Optional<UserProfile> user = userProfileRepository.findByUserNameIgnoreCase(normalizedUsername);
        if (user.isEmpty()) {
            return "I couldn't find `" + normalizedUsername + "` in the database.";
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

        response.append(".");
        return response.toString();
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

    private String normalizeUsername(String username) {
        return StringUtils.trimOrNull(username);
    }

    private String formatBalance(double balance) {
        if (Math.abs(balance - Math.rint(balance)) < 0.0001d) {
            return String.valueOf((long) Math.rint(balance));
        }
        return String.format("%.1f", balance);
    }

    private record LeaveBalanceRow(String leaveName, double remainingBalance) {
    }
}
