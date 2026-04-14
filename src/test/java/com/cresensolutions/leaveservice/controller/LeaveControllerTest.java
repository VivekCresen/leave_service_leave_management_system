package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.*;
import com.cresensolutions.leaveservice.service.LeaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveControllerTest {

    @Mock private LeaveService leaveService;
    @InjectMocks private LeaveController leaveController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LeaveResponse sampleLeaveResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(leaveController)
                .setControllerAdvice(new com.cresensolutions.leaveservice.exception.GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        // Use mutable ArrayList so Jackson can serialize the list inside PageImpl
        List<LeaveDateDto> dates = new ArrayList<>();
        dates.add(new LeaveDateDto(LocalDate.now(), "FULL"));
        List<Long> notifyIds = new ArrayList<>();

        sampleLeaveResponse = new LeaveResponse(
                1L, 1L, "John Doe", "john@example.com",
                1, "ANNUAL_LEAVE", dates,
                "Vacation", null, null, true, "PENDING",
                null, null, LocalDate.now(), LocalDate.now(), notifyIds);
    }

    private Page<LeaveResponse> singlePage() {
        List<LeaveResponse> content = new ArrayList<>();
        content.add(sampleLeaveResponse);
        return new PageImpl<>(content, PageRequest.of(0, 50), 1);
    }

    @Test
    void createLeave_validRequest_returns200() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, null);

        when(leaveService.createLeave(any())).thenReturn(sampleLeaveResponse);

        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void submitLeaveApplication_validRequest_returns200() throws Exception {
        CreateLeaveRequest req = new CreateLeaveRequest(
                1L, null, 1,
                List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Vacation", null, null, true, null);

        when(leaveService.createLeave(any())).thenReturn(sampleLeaveResponse);

        mockMvc.perform(post("/api/leaves/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getAllLeaves_returnsPage() throws Exception {
        when(leaveService.getAllLeaves(0, 50)).thenReturn(singlePage());

        mockMvc.perform(get("/api/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getLeaveById_found_returns200() throws Exception {
        when(leaveService.getLeaveById(1L)).thenReturn(sampleLeaveResponse);

        mockMvc.perform(get("/api/leaves/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getLeavesByUserId_returnsPage() throws Exception {
        when(leaveService.getLeavesByUserId(eq(1L), eq(0), eq(50))).thenReturn(singlePage());

        mockMvc.perform(get("/api/leaves/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getLeavesByUsername_returnsPage() throws Exception {
        when(leaveService.getLeavesByUsername(eq("john"), eq(0), eq(200))).thenReturn(singlePage());

        mockMvc.perform(get("/api/leaves/by-username/john"))
                .andExpect(status().isOk());
    }

    @Test
    void getLeaveHistoryGrid_returnsPage() throws Exception {
        when(leaveService.getLeavesByUsername(eq("john"), eq(0), eq(200))).thenReturn(singlePage());

        mockMvc.perform(get("/api/leaves/history/john"))
                .andExpect(status().isOk());
    }

    @Test
    void getLeavesByManagerUsername_returnsPage() throws Exception {
        when(leaveService.getLeavesByManagerUsername(eq("manager1"), eq(0), eq(200))).thenReturn(singlePage());

        mockMvc.perform(get("/api/leaves/by-manager/manager1"))
                .andExpect(status().isOk());
    }

    @Test
    void updateLeaveStatus_validRequest_returns200() throws Exception {
        UpdateLeaveStatusRequest req = new UpdateLeaveStatusRequest("manager1", "APPROVED", null);
        when(leaveService.updateLeaveStatus(eq(1L), any())).thenReturn(sampleLeaveResponse);

        mockMvc.perform(put("/api/leaves/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateLeave_validRequest_returns200() throws Exception {
        UpdateLeaveRequest req = new UpdateLeaveRequest(
                1, List.of(new LeaveDateDto(LocalDate.now(), "FULL")),
                "Updated reason", null, null, null);
        when(leaveService.updateLeave(eq(1L), any())).thenReturn(sampleLeaveResponse);

        mockMvc.perform(put("/api/leaves/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deletePendingLeave_returns204() throws Exception {
        doNothing().when(leaveService).deletePendingLeave(1L);

        mockMvc.perform(delete("/api/leaves/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getLeaveTypes_returnsList() throws Exception {
        LeaveTypeResponse typeResponse = new LeaveTypeResponse(
                1, "Annual Leave", "ANNUAL_LEAVE", null, 20, null, null, null);
        when(leaveService.getLeaveTypes()).thenReturn(List.of(typeResponse));

        mockMvc.perform(get("/api/leaves/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leaveName").value("Annual Leave"));
    }

    @Test
    void createLeaveType_validRequest_returns200() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Sick Leave", "SICK_LEAVE", null, 10, null);
        LeaveTypeResponse typeResponse = new LeaveTypeResponse(
                2, "Sick Leave", "SICK_LEAVE", null, 10, null, null, null);
        when(leaveService.createLeaveType(any())).thenReturn(typeResponse);

        mockMvc.perform(post("/api/leaves/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveName").value("Sick Leave"));
    }

    @Test
    void updateLeaveType_validRequest_returns200() throws Exception {
        CreateLeaveTypeRequest req = new CreateLeaveTypeRequest(
                "Annual Leave", "ANNUAL_LEAVE", null, 25, null);
        LeaveTypeResponse typeResponse = new LeaveTypeResponse(
                1, "Annual Leave", "ANNUAL_LEAVE", null, 25, null, null, null);
        when(leaveService.updateLeaveType(eq(1), any())).thenReturn(typeResponse);

        mockMvc.perform(put("/api/leaves/types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteLeaveType_returns200() throws Exception {
        doNothing().when(leaveService).deleteLeaveType(1);

        mockMvc.perform(delete("/api/leaves/types/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getNotifyUsers_returnsUserList() throws Exception {
        NotifyUserResponse notifyUser = new NotifyUserResponse(2L, "Jane", "jane@example.com", "EMPLOYEE");
        when(leaveService.getNotifyUsers("john")).thenReturn(List.of(notifyUser));

        mockMvc.perform(get("/api/leaves/notify-users").param("username", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Jane"));
    }

    @Test
    void createLeave_missingReason_returns400() throws Exception {
        String invalidJson = """
                {"userId":1,"leaveTypeId":1,"leaveDates":[{"date":"2026-04-10","dayType":"FULL"}],"reason":""}
                """;

        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
