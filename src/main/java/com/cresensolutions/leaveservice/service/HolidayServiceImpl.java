package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;
import com.cresensolutions.leaveservice.exception.ResourceNotFoundException;
import com.cresensolutions.leaveservice.model.PublicHoliday;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HolidayServiceImpl implements HolidayService {

    private final PublicHolidayRepository holidayRepository;

    public HolidayServiceImpl(PublicHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    public List<HolidayResponse> getHolidays(Integer year) {
        List<PublicHoliday> holidays = year != null
                ? holidayRepository.findByYear(year)
                : holidayRepository.findAllOrderedByDate();
        return holidays.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public HolidayResponse createHoliday(CreateHolidayRequest request) {
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

    @Override
    @Transactional
    public HolidayResponse updateHoliday(Long id, CreateHolidayRequest request) {
        PublicHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id: " + id));
        holiday.update(
                request.name().trim(),
                request.date(),
                request.description() != null ? request.description().trim() : null
        );
        return toResponse(holidayRepository.save(holiday));
    }

    @Override
    @Transactional
    public void deleteHoliday(Long id) {
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
