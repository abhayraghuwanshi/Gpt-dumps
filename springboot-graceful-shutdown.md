# Spring Boot – Graceful Shutdown for HTTP, Sync, and Scheduler Services

## 1. Overview
When Kubernetes issues a `SIGTERM` to terminate a pod (e.g., during rolling deployments), the application must:
- Stop receiving new traffic (HTTP)
- Stop scheduling new jobs
- Complete in-progress work (HTTP/sync tasks/schedulers)
- Clean up resources (DB, caches, threads)

---

## 2. HTTP Endpoint Shutdown Handling

### 2.1 Refuse New HTTP Requests Gracefully
Use Spring Boot's Actuator readiness integration to tell Kubernetes **"this pod is not ready"** before shutdown, so it stops routing HTTP traffic.

```java
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

@Component
public class GracefulShutdown {

    private final ApplicationEventPublisher publisher;

    public GracefulShutdown(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PreDestroy
    public void onShutdown() {
        System.out.println("[GracefulShutdown] SIGTERM received. Marking app as NOT READY...");
        publisher.publishEvent(AvailabilityChangeEvent.publish(this, ReadinessState.REFUSING_TRAFFIC));

        try {
            Thread.sleep(2000); // Give K8s time to unregister this pod
        } catch (InterruptedException ignored) {}
    }
}
```

---

### 2.2 (Optional) Intercept HTTP Requests in Shutdown Mode
You can add a filter to reject or queue HTTP requests if shutdown is in progress:

```java
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class ShutdownAwareFilter implements Filter {

    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public static void markShuttingDown() {
        shuttingDown.set(true);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (shuttingDown.get()) {
            ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            res.getWriter().write("Shutting down, service unavailable.");
        } else {
            chain.doFilter(req, res);
        }
    }
}
```

Update `GracefulShutdown` to use it:
```java
@PreDestroy
public void onShutdown() {
    ShutdownAwareFilter.markShuttingDown();
    // ... rest of the shutdown code
}
```

---

## 3. Schedulers and Sync Tasks

### 3.1 Schedulers: Stop Submitting New Tasks

```java
@Component
public class MyScheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean shuttingDown = false;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(() -> {
            if (shuttingDown) return;
            System.out.println("Running scheduled job...");
        }, 0, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
```

---

### 3.2 Sync Tasks: Let Them Finish
If sync tasks (like DB updates) are triggered from HTTP or services:
- Don’t forcibly cancel
- Allow them to complete in the `terminationGracePeriodSeconds`

---

## 4. Kubernetes Configuration

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10

terminationGracePeriodSeconds: 30
```

---

## 5. Spring Boot Properties

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
```

---

## Summary Table

| Component      | Action                                                                 |
|----------------|------------------------------------------------------------------------|
| **HTTP**       | Mark app as not ready (`REFUSING_TRAFFIC`) so K8s stops routing traffic |
| **Filter**     | Optionally reject requests with 503 during shutdown                     |
| **Schedulers** | Stop submitting new jobs; shut down executors gracefully                |
| **Sync Tasks** | Let in-flight tasks complete naturally                                  |
| **Kubernetes** | Use probes and `terminationGracePeriodSeconds` to allow graceful exit   |

