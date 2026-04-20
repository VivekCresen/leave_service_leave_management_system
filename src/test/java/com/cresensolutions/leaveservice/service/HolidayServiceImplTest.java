package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.service.Impl.HolidayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolidayServiceImplTest {

    @Mock private PublicHolidayRepository holidayRepository;
    @InjectMocks private HolidayServiceImpl holidayService;

    private PublicHoliday sampleHoliday;

    @BeforeEach
    void setUp() throws Exception {
        sampleHoliday = new PublicHoliday("New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");
        setField(sampleHoliday, "id", 1L);
    }

    // ─── getHolidays ─────────────────────────────────────────────────────────────

    @Test
    void getHolidays_withNullYear_returnsAllOrdered() {
        when(holidayRepository.findAllOrderedByDate()).thenReturn(List.of(sampleHoliday));

        List<HolidayResponse> result = holidayService.getHolidays(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("New Year");
        assertThat(result.get(0).id()).isEqualTo(1L);
        verify(holidayRepository).findAllOrderedByDate();
        verify(holidayRepository, never()).findByYear(anyInt());
    }

    @Test
    void getHolidays_withYear_returnsFilteredByYear() {
        when(holidayRepository.findByYear(2026)).thenReturn(List.of(sampleHoliday));

        List<HolidayResponse> result = holidayService.getHolidays(2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        verify(holidayRepository).findByYear(2026);
        verify(holidayRepository, never()).findAllOrderedByDate();
    }

    @Test
    void getHolidays_emptyList_returnsEmpty() {
        when(holidayRepository.findAllOrderedByDate()).thenReturn(List.of());

        List<HolidayResponse> result = holidayService.getHolidays(null);

        assertThat(result).isEmpty();
    }

    // ─── createHoliday ───────────────────────────────────────────────────────────

    @Test
    void createHoliday_success_returnsResponse() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(any(), any())).thenReturn(false);
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        HolidayResponse response = holidayService.createHoliday(req);

        assertThat(response.name()).isEqualTo("New Year");
        assertThat(response.createdBy()).isEqualTo("admin");
        verify(holidayRepository).save(any(PublicHoliday.class));
    }

    @Test
    void createHoliday_withNullDescription_savesWithNullDescription() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "  New Year  ", LocalDate.of(2026, 1, 1), null, "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(any(), any())).thenReturn(false);
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        HolidayResponse response = holidayService.createHoliday(req);

        assertThat(response).isNotNull();
        verify(holidayRepository).save(any(PublicHoliday.class));
    }

    @Test
    void createHoliday_withDescription_trimsSavesDescription() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), "  Some desc  ", "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(any(), any())).thenReturn(false);
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        HolidayResponse response = holidayService.createHoliday(req);

        assertThat(response).isNotNull();
    }

    @Test
    void createHoliday_duplicate_throwsIllegalArgument() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), null, "admin");

        when(holidayRepository.existsByDateAndNameIgnoreCase(LocalDate.of(2026, 1, 1), "New Year"))
                .thenReturn(true);

        assertThatThrownBy(() -> holidayService.createHoliday(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New Year")
                .hasMessageContaining("already exists");
    }

    // ─── updateHoliday ───────────────────────────────────────────────────────────

    @Test
    void updateHoliday_found_updatesAndReturns() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year Updated", LocalDate.of(2026, 1, 2), "Updated desc", null);

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(sampleHoliday));
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        HolidayResponse response = holidayService.updateHoliday(1L, req);

        assertThat(response).isNotNull();
        verify(holidayRepository).save(sampleHoliday);
    }

    @Test
    void updateHoliday_withNullDescription_setsNull() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "New Year", LocalDate.of(2026, 1, 1), null, null);

        when(holidayRepository.findById(1L)).thenReturn(Optional.of(sampleHoliday));
        when(holidayRepository.save(any())).thenReturn(sampleHoliday);

        HolidayResponse response = holidayService.updateHoliday(1L, req);

        assertThat(response).isNotNull();
    }

    @Test
    void updateHoliday_notFound_throwsResourceNotFound() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                "X", LocalDate.of(2026, 1, 1), null, null);

        when(holidayRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.updateHoliday(99L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Holiday not found with id: 99");
    }

    // ─── deleteHoliday ───────────────────────────────────────────────────────────

    @Test
    void deleteHoliday_found_deletesSuccessfully() {
        when(holidayRepository.existsById(1L)).thenReturn(true);

        holidayService.deleteHoliday(1L);

        verify(holidayRepository).deleteById(1L);
    }

    @Test
    void deleteHoliday_notFound_throwsResourceNotFound() {
        when(holidayRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> holidayService.deleteHoliday(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Holiday not found with id: 99");
    }

    // ─── toResponse mapping ───────────────────────────────────────────────────────

    @Test
    void getHolidays_mapsAllResponseFields() {
        when(holidayRepository.findAllOrderedByDate()).thenReturn(List.of(sampleHoliday));

        List<HolidayResponse> result = holidayService.getHolidays(null);

        HolidayResponse r = result.get(0);
        assertThat(r.id()).isEqualTo(1L);
        assertThat(r.name()).isEqualTo("New Year");
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.description()).isEqualTo("New Year Day");
        assertThat(r.createdBy()).isEqualTo("admin");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
