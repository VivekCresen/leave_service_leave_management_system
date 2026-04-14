package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HolidayControllerTest {

    @Mock private PublicHolidayRepository holidayRepository;
    @InjectMocks private HolidayController holidayController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PublicHoliday sampleHoliday;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(holidayController)
                .setControllerAdvice(new com.cresensolutions.leaveservice.exception.GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        sampleHoliday = new PublicHoliday("New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");
        setField(sampleHoliday, "id", 1L);
    }

    @Test
    void getHolidays_noYear_returnsAll() throws Exception {
        when(holidayRepository.findAllOrderedByDate()).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New Year"));
    }

    @Test
    void getHolidays_withYear_returnsFiltered() throws Exception {
        when(holidayRepository.findByYear(2026)).thenReturn(List.of(sampleHoliday));

        mockMvc.perform(get("/api/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New Year"));
    }

    @Test
    void createHoliday_validRequest_returns201() throws Exception {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(any(), any())).thenReturn(false);
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Year"));
    }

    @Test
    void createHoliday_duplicate_returns400() throws Exception {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), null, "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateHoliday_found_returns200() throws Exception {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year Updated", LocalDate.of(2026, 1, 1), null, null);

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(sampleHoliday));
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        mockMvc.perform(put("/api/holidays/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateHoliday_notFound_returns404() throws Exception {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "X", LocalDate.of(2026, 1, 1), null, null);

        when(holidayRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/holidays/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteHoliday_found_returns204() throws Exception {
        when(holidayRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/holidays/1"))
                .andExpect(status().isNoContent());

        verify(holidayRepository).deleteById(1L);
    }

    @Test
    void deleteHoliday_notFound_returns404() throws Exception {
        when(holidayRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/holidays/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createHoliday_missingName_returns400() throws Exception {
        String invalidJson = """
                {"date":"2026-01-01","description":"test"}
                """;

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
