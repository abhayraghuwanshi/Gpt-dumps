@Service
public class DynamicTransactionScheduler {

    private final TaskScheduler taskScheduler;
    private final HolidayService holidayService;
    private final TransactionBookingService bookingService;
    private final BookingRepository bookingRepository;
    private final RestTemplate restTemplate; // For calling email service

    // In-memory store of scheduled tasks for tracking
    private final Map<LocalDate, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    public DynamicTransactionScheduler(TaskScheduler taskScheduler,
                                         HolidayService holidayService,
                                         TransactionBookingService bookingService,
                                         BookingRepository bookingRepository,
                                         RestTemplate restTemplate) {
        this.taskScheduler = taskScheduler;
        this.holidayService = holidayService;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.restTemplate = restTemplate;
    }

    public void scheduleNextBooking(int year, int month) {
        LocalDate lastBusinessDay = holidayService.getLastBusinessDay(year, month);
        Date executionTime = Date.from(lastBusinessDay.atTime(18, 0)
            .atZone(ZoneId.systemDefault()).toInstant());

        // Persist schedule to DB if not already present
        if (bookingRepository.findByBookingDate(lastBusinessDay).isEmpty()) {
            bookingRepository.save(new ScheduledBooking(year, month, lastBusinessDay));
        }

        ScheduledFuture<?> futureTask = taskScheduler.schedule(() -> {
            bookingService.processTransactions(lastBusinessDay);
            bookingRepository.deleteByBookingDate(lastBusinessDay);
            scheduledTasks.remove(lastBusinessDay);
            
            // Build payload for email notification
            Map<String, Object> emailPayload = new HashMap<>();
            emailPayload.put("recipient", "user@example.com");
            emailPayload.put("subject", "Transaction Processed");
            emailPayload.put("body", "Your transaction for " + lastBusinessDay + 
                " has been successfully processed. [Additional details]");
            
            // Call email notification microservice
            restTemplate.postForEntity("http://email-service/api/send-email", 
                                         emailPayload, String.class);
            
        }, executionTime);

        scheduledTasks.put(lastBusinessDay, futureTask);
    }

    public Set<LocalDate> getScheduledDates() {
        return scheduledTasks.keySet();
    }

    public boolean cancelScheduledBooking(LocalDate date) {
        if (scheduledTasks.containsKey(date)) {
            scheduledTasks.get(date).cancel(false);
            scheduledTasks.remove(date);
            bookingRepository.deleteByBookingDate(date);
            return true;
        }
        return false;
    }
}


@RestController
@RequestMapping("/api")
public class SchedulerController {
    
    private final DynamicTransactionScheduler scheduler;

    public SchedulerController(DynamicTransactionScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/scheduled-transactions")
    public Set<LocalDate> getScheduledTransactions() {
        return scheduler.getScheduledDates();
    }

    @DeleteMapping("/scheduled-transactions/{date}")
    public String cancelScheduledTransaction(@PathVariable String date) {
        LocalDate bookingDate = LocalDate.parse(date);
        boolean removed = scheduler.cancelScheduledBooking(bookingDate);
        return removed ? "Cancelled transaction for " + date : "Transaction not found";
    }

    @PostMapping("/manual-schedule")
    public String manualSchedule(@RequestParam String date) {
        try {
            LocalDate bookingDate = LocalDate.parse(date);
            scheduler.scheduleBookingForDate(bookingDate);
            return "Transaction manually scheduled for " + bookingDate;
        } catch (Exception e) {
            return "Error scheduling transaction: " + e.getMessage();
        }
    }
}

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class ScheduledBooking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private int year;
    private int month;
    private LocalDate bookingDate;

    public ScheduledBooking() {}

    public ScheduledBooking(int year, int month, LocalDate bookingDate) {
        this.year = year;
        this.month = month;
        this.bookingDate = bookingDate;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }
}


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class StartupTaskLoader implements CommandLineRunner {

    private final BookingRepository bookingRepository;
    private final DynamicTransactionScheduler scheduler;

    public StartupTaskLoader(BookingRepository bookingRepository, DynamicTransactionScheduler scheduler) {
        this.bookingRepository = bookingRepository;
        this.scheduler = scheduler;
    }

    @Override
    public void run(String... args) {
        List<ScheduledBooking> bookings = bookingRepository.findAll();
        bookings.forEach(b -> scheduler.scheduleNextBooking(b.getYear(), b.getMonth()));
    }
}

