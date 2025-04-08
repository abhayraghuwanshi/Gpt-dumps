package org.learn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.learn.DynamicTransactionScheduler;
import org.learn.SchedulerController;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerControllerTest {

    @Test
    void testManualSchedule_success() throws Exception {
        // Arrange
        DynamicTransactionScheduler scheduler = mock(DynamicTransactionScheduler.class);
        SchedulerController controller = new SchedulerController(scheduler);

        String dateStr = "2025-04-10";
        LocalDate date = LocalDate.parse(dateStr);

        // Act
        ResponseEntity<String> response = controller.manualSchedule(dateStr);

        // Assert
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains(dateStr));

        verify(scheduler, times(1)).scheduleBookingForDate(date);
    }

    @Test
    void testManualSchedule_exception() throws Exception {
        // Arrange
        DynamicTransactionScheduler scheduler = mock(DynamicTransactionScheduler.class);
        SchedulerController controller = new SchedulerController(scheduler);

        String dateStr = "invalid-date";

        // Act
        ResponseEntity<String> response = controller.manualSchedule(dateStr);

        // Assert
        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Error scheduling transaction"));
    }
}

=============================================================================


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learn.DynamicTransactionSchedulerImpl;
import org.learn.service.TransactionBookingService;
import org.learn.service.DateService;
import org.learn.service.ReferentialServiceApi;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class DynamicTransactionSchedulerImplTest {

    private TransactionBookingService bookingService;
    private DateService dateService;
    private ReferentialServiceApi referentialServiceApi;
    private DynamicTransactionSchedulerImpl scheduler;

    @BeforeEach
    void setUp() {
        bookingService = mock(TransactionBookingService.class);
        dateService = mock(DateService.class);
        referentialServiceApi = mock(ReferentialServiceApi.class);

        scheduler = new DynamicTransactionSchedulerImpl(
                bookingService,
                dateService,
                referentialServiceApi
        );
    }

    @Test
    void testScheduleBookingForDate_shouldProcessBooking() throws Exception {
        // Given
        LocalDate bookingDate = LocalDate.of(2025, 4, 10);
        when(dateService.isHoliday(bookingDate)).thenReturn(false);

        // When
        scheduler.scheduleBookingForDate(bookingDate);

        // Then
        verify(bookingService, times(1)).processTransactions(bookingDate);
    }

    @Test
    void testScheduleBookingForDate_shouldUsePreviousWorkingDayOnHoliday() throws Exception {
        // Given
        LocalDate holiday = LocalDate.of(2025, 4, 13);
        LocalDate previousWorkingDay = LocalDate.of(2025, 4, 11);

        when(dateService.isHoliday(holiday)).thenReturn(true);
        when(dateService.getPreviousWorkingDay(holiday)).thenReturn(previousWorkingDay);

        // When
        scheduler.scheduleBookingForDate(holiday);

        // Then
        verify(bookingService, times(1)).processTransactions(previousWorkingDay);
    }
}
