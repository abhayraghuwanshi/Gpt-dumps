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


package org.learn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicTransactionSchedulerImplTest {

    @Mock
    private Scheduler scheduler;

    private DynamicTransactionSchedulerImpl transactionScheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionScheduler = new DynamicTransactionSchedulerImpl(scheduler);
    }

    @Test
    void testScheduleBookingForDate_success() throws Exception {
        // Given
        LocalDate bookingDate = LocalDate.of(2025, 4, 10);

        // When
        transactionScheduler.scheduleBookingForDate(bookingDate);

        // Then
        ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobCaptor.capture(), triggerCaptor.capture());

        JobDetail capturedJob = jobCaptor.getValue();
        Trigger capturedTrigger = triggerCaptor.getValue();

        assertEquals("bookingJob-" + bookingDate, capturedJob.getKey().getName());
        assertEquals("trigger-" + bookingDate, capturedTrigger.getKey().getName());

        Date expectedDate = Date.from(bookingDate.atTime(18, 0)
                .atZone(ZoneId.systemDefault()).toInstant());
        assertEquals(expectedDate, capturedTrigger.getStartTime());
    }
}
