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

class SchedulerControllerTest {

    @Mock
    private DynamicTransactionScheduler scheduler;

    @InjectMocks
    private SchedulerController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testManualSchedule_Success() {
        String date = "2025-04-10";
        ResponseEntity<String> response = controller.manualSchedule(date);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Transaction manually scheduled for"));
        verify(scheduler, times(1)).scheduleBookingForDate(LocalDate.parse(date));
    }

    @Test
    void testManualSchedule_Failure() {
        String date = "invalid-date";
        ResponseEntity<String> response = controller.manualSchedule(date);

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Error scheduling transaction"));
    }

    @Test
    void testGetScheduledJobs_Success() throws Exception {
        List<String> jobs = Arrays.asList("job1", "job2");
        when(scheduler.getScheduledJobs()).thenReturn(jobs);

        ResponseEntity<List<String>> response = controller.getScheduledJobs();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(jobs, response.getBody());
    }

    @Test
    void testGetScheduledJobs_Failure() throws Exception {
        when(scheduler.getScheduledJobs()).thenThrow(new RuntimeException("Internal error"));

        ResponseEntity<List<String>> response = controller.getScheduledJobs();

        assertEquals(500, response.getStatusCodeValue());
        assertEquals(Collections.singletonList("Error retrieving jobs: Internal error"), response.getBody());
    }

    @Test
    void testCancelSchedule_Success() throws Exception {
        String date = "2025-04-10";
        when(scheduler.cancelScheduledBooking(LocalDate.parse(date))).thenReturn(true);

        ResponseEntity<String> response = controller.cancelSchedule(date);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Cancelled booking for"));
    }

    @Test
    void testCancelSchedule_NotFound() throws Exception {
        String date = "2025-04-10";
        when(scheduler.cancelScheduledBooking(LocalDate.parse(date))).thenReturn(false);

        ResponseEntity<String> response = controller.cancelSchedule(date);

        assertEquals(404, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("No booking found"));
    }

    @Test
    void testCancelSchedule_Exception() {
        String date = "invalid-date";
        ResponseEntity<String> response = controller.cancelSchedule(date);

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("Error canceling transaction"));
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
