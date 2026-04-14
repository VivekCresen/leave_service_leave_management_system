package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateHolidayRequest;
import com.cresensolutions.leaveservice.dto.HolidayResponse;

import java.util.List;

public interface HolidayService {

    List<HolidayResponse> getHolidays(Integer year);

    HolidayResponse createHoliday(CreateHolidayRequest request);

    HolidayResponse updateHoliday(Long id, CreateHolidayRequest request);

    void deleteHoliday(Long id);
}
