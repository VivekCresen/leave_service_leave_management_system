package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.service.HolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public List<HolidayResponse> getHolidays(@RequestParam(required = false) Integer year) {
        return holidayService.getHolidays(year);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse createHoliday(@Valid @RequestBody CreateHolidayRequest request) {
        return holidayService.createHoliday(request);
    }

    @PutMapping("/{id}")
    public HolidayResponse updateHoliday(
            @PathVariable Long id,
            @Valid @RequestBody CreateHolidayRequest request
    ) {
        return holidayService.updateHoliday(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) {
        holidayService.deleteHoliday(id);
    }
}
