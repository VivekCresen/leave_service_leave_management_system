package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/holidays")
public class  HolidayController {

    private final PublicHolidayRepository holidayRepository;

    public HolidayController(PublicHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping
    public List<HolidayResponse> getHolidays(@RequestParam(required = false) Integer year) {
        List<PublicHoliday> holidays = year != null
                ? holidayRepository.findByYear(year)
                : holidayRepository.findAllOrderedByDate();
        return holidays.stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse createHoliday(@Valid @RequestBody CreateHolidayRequest request) {
        if (holidayRepository.existsByDateAndNameIgnoreCase(request.date(), request.name())) {
            throw new IllegalArgumentException(
                    "A holiday named '" + request.name() + "' already exists on " + request.date());
        }
        PublicHoliday holiday = new PublicHoliday(
                request.name().trim(),
                request.date(),
                request.description() != null ? request.description().trim() : null,
                request.createdBy()
        );
        return toResponse(holidayRepository.save(holiday));
    }

    @PutMapping("/{id}")
    public HolidayResponse updateHoliday(
            @PathVariable Long id,
            @Valid @RequestBody CreateHolidayRequest request
    ) {
        PublicHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id: " + id));
        holiday.update(
                request.name().trim(),
                request.date(),
                request.description() != null ? request.description().trim() : null
        );
        return toResponse(holidayRepository.save(holiday));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday not found with id: " + id);
        }
        holidayRepository.deleteById(id);
    }

    private HolidayResponse toResponse(PublicHoliday h) {
        return new HolidayResponse(h.getId(), h.getName(), h.getDate(),
                h.getDescription(), h.getCreatedBy(), h.getCreatedAt());
    }
}
