package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    @Query("SELECT h FROM PublicHoliday h ORDER BY h.date ASC")
    List<PublicHoliday> findAllOrderedByDate();

    @Query("SELECT h FROM PublicHoliday h WHERE YEAR(h.date) = :year ORDER BY h.date ASC")
    List<PublicHoliday> findByYear(@Param("year") int year);

    boolean existsByDateAndNameIgnoreCase(LocalDate date, String name);

    @Query("SELECT h FROM PublicHoliday h WHERE h.date BETWEEN :from AND :to ORDER BY h.date ASC")
    List<PublicHoliday> findByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
