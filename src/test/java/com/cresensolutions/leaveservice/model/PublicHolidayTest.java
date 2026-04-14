package com.cresensolutions.leaveservice.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PublicHolidayTest {

    @Test
    void constructor_setsAllFields() {
        PublicHoliday holiday = new PublicHoliday(
                "New Year", LocalDate.of(2026, 1, 1), "New Year Day", "admin");

        assertThat(holiday.getName()).isEqualTo("New Year");
        assertThat(holiday.getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(holiday.getDescription()).isEqualTo("New Year Day");
        assertThat(holiday.getCreatedBy()).isEqualTo("admin");
    }

    @Test
    void update_changesNameDateDescription() {
        PublicHoliday holiday = new PublicHoliday(
                "New Year", LocalDate.of(2026, 1, 1), "Old desc", "admin");

        holiday.update("New Year Updated", LocalDate.of(2026, 1, 2), "New desc");

        assertThat(holiday.getName()).isEqualTo("New Year Updated");
        assertThat(holiday.getDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(holiday.getDescription()).isEqualTo("New desc");
    }
}
