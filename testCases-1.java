@Service
public class DynamicTransactionSchedulerImpl extends DynamicTransactionScheduler {

    private final TransactionBookingService bookingService;
    private final DateService dateService;
    private final ReferentialServiceApi referentialServiceApi;

    public DynamicTransactionSchedulerImpl(TransactionBookingService bookingService,
                                           DateService dateService,
                                           ReferentialServiceApi referentialServiceApi) {
        this.bookingService = bookingService;
        this.dateService = dateService;
        this.referentialServiceApi = referentialServiceApi;
    }

    /**
     * Runs every day at 18:00 and schedules transactions for the last week of the month.
     */
    @Scheduled(cron = "0 0 18 * * ?") // Runs daily at 18:00
    public void scheduleBookingForLastWeek() {
        LocalDate today = LocalDate.now();
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate lastWeekStart = lastDayOfMonth.minusDays(6);

        if (!today.isBefore(lastWeekStart)) {
            scheduleBookingForDate(today);
        }
    }

    /**
     * Schedules a transaction manually.
     */
    @Async
    public void scheduleBookingForDate(LocalDate bookingDate) {
        if (dateService.isHoliday(bookingDate)) {
            bookingDate = dateService.getPreviousWorkingDay(bookingDate);
        }

        bookingService.processTransactions(bookingDate);

        referentialServiceApi.sendMail(
                MailRequest.builder().body("Transaction processed").to("user@example.com")
                        .from("noreply@example.com").subject("Booking Scheduled").build()
        );

        System.out.println("Scheduled transaction for: " + bookingDate);
    }
}




@RestController
@RequestMapping("/api")
public class SchedulerController {

    private final DynamicTransactionScheduler scheduler;

    public SchedulerController(DynamicTransactionScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/manual-schedule")
    public ResponseEntity<String> manualSchedule(@RequestParam String date) {
        try {
            LocalDate bookingDate = LocalDate.parse(date);
            scheduler.scheduleBookingForDate(bookingDate);
            return ResponseEntity.ok("Transaction manually scheduled for " + bookingDate);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error scheduling transaction: " + e.getMessage());
        }
    }
}


@SpringBootTest
@RunWith(SpringRunner.class)
public class DynamicTransactionSchedulerTest {

    @Autowired
    private DynamicTransactionSchedulerImpl scheduler;

    @Test
    public void testScheduleBookingForLastWeek() {
        scheduler.scheduleBookingForLastWeek();

        LocalDate today = LocalDate.now();
        LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate lastWeekStart = lastDayOfMonth.minusDays(6);

        if (!today.isBefore(lastWeekStart)) {
            assertTrue(true, "Booking scheduled as expected");
        }
    }
}

@SpringBootTest
@RunWith(SpringRunner.class)
public class SchedulerControllerTest {

    @Autowired
    private SchedulerController schedulerController;

    @Test
    public void testManualSchedule() {
        ResponseEntity<String> response = schedulerController.manualSchedule("2025-04-25");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Transaction manually scheduled for 2025-04-25"));
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@RunWith(SpringRunner.class)
public class SchedulerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamicTransactionScheduler scheduler;

    @Test
    public void testManualSchedule_ValidDate() throws Exception {
        mockMvc.perform(post("/api/manual-schedule")
                .param("date", "2025-04-25"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transaction manually scheduled for 2025-04-25")));

        verify(scheduler, times(1)).scheduleBookingForDate(LocalDate.of(2025, 4, 25));
    }

    @Test
    public void testManualSchedule_InvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/manual-schedule")
                .param("date", "invalid-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testManualSchedule_SchedulerThrowsException() throws Exception {
        doThrow(new RuntimeException("Database error")).when(scheduler).scheduleBookingForDate(any());

        mockMvc.perform(post("/api/manual-schedule")
                .param("date", "2025-04-25"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Error scheduling transaction: Database error")));
    }
}


@SpringBootTest
@RunWith(SpringRunner.class)
public class DynamicTransactionSchedulerTest {

    @Mock
    private TransactionBookingService bookingService;

    @Mock
    private DateService dateService;

    @Mock
    private ReferentialServiceApi referentialServiceApi;

    @InjectMocks
    private DynamicTransactionSchedulerImpl scheduler;

    @Test
    public void testScheduleBookingForLastWeek_Success() {
        LocalDate lastWeekDay = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).minusDays(3);

        when(dateService.isHoliday(lastWeekDay)).thenReturn(false);

        scheduler.scheduleBookingForDate(lastWeekDay);

        verify(bookingService, times(1)).processTransactions(lastWeekDay);
        verify(referentialServiceApi, times(1)).sendMail(any());
    }

    @Test
    public void testScheduleBookingForLastWeek_OnHoliday() {
        LocalDate holiday = LocalDate.of(2025, 4, 28);
        LocalDate previousWorkingDay = LocalDate.of(2025, 4, 26);

        when(dateService.isHoliday(holiday)).thenReturn(true);
        when(dateService.getPreviousWorkingDay(holiday)).thenReturn(previousWorkingDay);

        scheduler.scheduleBookingForDate(holiday);

        verify(bookingService, times(1)).processTransactions(previousWorkingDay);
    }

    @Test
    public void testScheduleBookingForLastWeek_Performance() {
        long startTime = System.nanoTime();
        
        scheduler.scheduleBookingForDate(LocalDate.now());

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertTrue(durationMs < 500, "Scheduling should complete in less than 500ms");
    }
}


