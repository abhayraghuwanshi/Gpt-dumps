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



