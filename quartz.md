Quartz Dual JobStore Integration with Custom Annotations
This document explains how to implement a dual Quartz scheduler setup in a Spring Boot application, allowing you to define jobs that are either memory-based (non-persistent) or database-based (persistent) using distinct custom annotations.

1. Core Concept: Why Two Schedulers?
In Quartz, the choice between in-memory storage (RAMJobStore) and database persistence (JDBCJobStore) is a global configuration for a Scheduler instance. A single Scheduler cannot manage some jobs in memory and others in a database simultaneously.

To achieve different persistence behaviors for different jobs, you must:

Configure two separate Scheduler instances.

Each Scheduler instance will be configured with its own JobStore type (one RAMJobStore, one JDBCJobStore).

Your application then schedules jobs with the appropriate Scheduler instance based on your desired persistence.

This solution provides fine-grained control over job persistence at the method annotation level.

2. Implementation Overview
The solution involves the following components:

Two Custom Annotations: @AbcQuartzRam and @AbcQuartzJdbc to mark methods for scheduling.

Two Quartz Scheduler Beans: Configured in Spring, one for RAMJobStore and one for JDBCJobStore.

Modified Job Registrar: Scans for both annotations and schedules jobs with the correct Scheduler instance.

Standard Job Runners: The MethodInvokingJob and NonConcurrentMethodInvokingJob remain largely unchanged.

Example Job Service: Demonstrates the usage of both new annotations.

Optional API Management: If you have an API to manage Quartz jobs, it will need to be adapted to interact with both schedulers.

3. Detailed Setup
3.1. Custom Annotations
You'll define two identical annotations, differing only in their name.

AbcQuartzRam.java

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbcQuartzRam {
    String cron();
    String name() default "";
    boolean disallowConcurrent() default false;
}

AbcQuartzJdbc.java

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbcQuartzJdbc {
    String cron();
    String name() default "";
    boolean disallowConcurrent() default false;
}

3.2. Quartz Configuration (QuartzConfig.java)
This is the most critical part. You define two SchedulerFactoryBean beans, each with a unique name and configured for a different JobStore.

ramSchedulerFactoryBean: Uses org.quartz.simpl.RAMJobStore.

jdbcSchedulerFactoryBean: Uses org.quartz.impl.jdbcjobstore.JobStoreTX (or JobStoreCMT). This requires a DataSource and PlatformTransactionManager to be defined and autowired. Remember to replace PostgreSQLDelegate with the appropriate delegate for your database.

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfig {

    // Autowire DataSource and PlatformTransactionManager for the JDBC Scheduler
    // Ensure these beans are configured elsewhere (e.g., in application.properties or another @Configuration)
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationContext applicationContext; // To pass Spring context to jobs

    /**
     * Configures the RAM-based (in-memory) Scheduler.
     */
    @Bean(name = "ramSchedulerFactoryBean")
    public SchedulerFactoryBean ramSchedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setApplicationContext(applicationContext);
        factory.setApplicationContextSchedulerContextKey("applicationContext");

        Properties quartzProperties = new Properties();
        quartzProperties.setProperty("org.quartz.scheduler.instanceName", "RamScheduler");
        quartzProperties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        quartzProperties.setProperty("org.quartz.threadPool.threadCount", "5");
        quartzProperties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore"); // RAM JobStore

        factory.setQuartzProperties(quartzProperties);
        factory.setStartDelay(0);
        factory.setAutoStartup(true);
        factory.setOverwriteExistingJobs(true);
        return factory;
    }

    /**
     * Configures the JDBC-based (persistent) Scheduler.
     */
    @Bean(name = "jdbcSchedulerFactoryBean")
    public SchedulerFactoryBean jdbcSchedulerFactoryBean() {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setApplicationContext(applicationContext);
        factory.setApplicationContextSchedulerContextKey("applicationContext");

        // Essential for JDBCJobStore: DataSource and TransactionManager
        factory.setDataSource(dataSource);
        factory.setTransactionManager(transactionManager);

        Properties quartzProperties = new Properties();
        quartzProperties.setProperty("org.quartz.scheduler.instanceName", "JdbcScheduler");
        quartzProperties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        quartzProperties.setProperty("org.quartz.threadPool.threadCount", "10");
        quartzProperties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX"); // JDBC JobStore
        quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate"); // <--- IMPORTANT: Change for your DB!
        quartzProperties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        quartzProperties.setProperty("org.quartz.jobStore.isClustered", "false"); // Set true for clustering
        // quartzProperties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "20000"); // For clustered mode

        factory.setQuartzProperties(quartzProperties);
        factory.setStartDelay(0);
        factory.setAutoStartup(true);
        factory.setOverwriteExistingJobs(true);
        return factory;
    }

    // Expose the two Scheduler instances as Spring beans
    @Bean(name = "ramScheduler")
    public Scheduler ramScheduler(@Qualifier("ramSchedulerFactoryBean") SchedulerFactoryBean factory) throws SchedulerException {
        return factory.getScheduler();
    }

    @Bean(name = "jdbcScheduler")
    public Scheduler jdbcScheduler(@Qualifier("jdbcSchedulerFactoryBean") SchedulerFactoryBean factory) throws SchedulerException {
        return factory.getScheduler();
    }

    // Example DataSource and TransactionManager beans (uncomment and configure for JDBC)
    /*
    @Bean
    public DataSource dataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/your_quartz_db");
        dataSource.setUsername("your_db_user");
        dataSource.setPassword("your_db_password");
        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
    }
    */
}

3.3. Job Registrar (AbcQuartzMethodRegistrar.java)
This class will now autowire both Scheduler instances using @Qualifier and route the scheduled methods to the correct scheduler based on which annotation is present.

import org.quartz.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.Map;

@Configuration
public class AbcQuartzMethodRegistrar implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(AbcQuartzMethodRegistrar.class);

    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("ramScheduler") // Inject the RAM scheduler
    private Scheduler ramScheduler;

    @Autowired
    @Qualifier("jdbcScheduler") // Inject the JDBC scheduler
    private Scheduler jdbcScheduler;

    @PostConstruct
    public void init() throws Exception {
        logger.info("Starting scan for @AbcQuartzRam and @AbcQuartzJdbc annotated methods...");

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);

        for (Object bean : beans.values()) {
            Method[] methods = bean.getClass().getDeclaredMethods();
            for (Method method : methods) {
                method.setAccessible(true);

                // Check for @AbcQuartzRam annotation
                AbcQuartzRam ramAnnotation = method.getAnnotation(AbcQuartzRam.class);
                if (ramAnnotation != null) {
                    scheduleMethod(bean, method, ramAnnotation.cron(), ramAnnotation.name(), ramAnnotation.disallowConcurrent(), ramScheduler, "RAM");
                    continue; // Move to next method once scheduled
                }

                // Check for @AbcQuartzJdbc annotation
                AbcQuartzJdbc jdbcAnnotation = method.getAnnotation(AbcQuartzJdbc.class);
                if (jdbcAnnotation != null) {
                    scheduleMethod(bean, method, jdbcAnnotation.cron(), jdbcAnnotation.name(), jdbcAnnotation.disallowConcurrent(), jdbcScheduler, "JDBC");
                }
            }
        }
        logger.info("Finished scanning and scheduling @AbcQuartz methods with dual schedulers.");
    }

    private void scheduleMethod(Object bean, Method method, String cron, String name, boolean disallowConcurrent, Scheduler targetScheduler, String schedulerType) throws SchedulerException {
        String jobName = name.isEmpty()
                ? bean.getClass().getSimpleName() + "." + method.getName()
                : name;

        Class<? extends Job> jobClass = disallowConcurrent
                ? NonConcurrentMethodInvokingJob.class
                : MethodInvokingJob.class;

        String jobGroup = "AbcQuartz" + schedulerType + "Group";
        String triggerGroup = "AbcQuartz" + schedulerType + "TriggerGroup";

        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .usingJobData(new JobDataMap(Map.of(
                        "bean", bean,
                        "method", method
                )))
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobName + "Trigger", triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .withMisfireHandlingInstructionDoNothing()
                )
                .build();

        if (targetScheduler.checkExists(jobDetail.getKey())) {
            logger.info("[{}] Job '{}' already exists. Rescheduling...", schedulerType, jobName);
            targetScheduler.rescheduleJob(trigger.getKey(), trigger);
        } else {
            logger.info("[{}] Scheduling new job: '{}' with cron: '{}'", schedulerType, jobName, cron);
            targetScheduler.scheduleJob(jobDetail, trigger);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

3.4. Job Runners (MethodInvokingJob.java, NonConcurrentMethodInvokingJob.java)
These classes remain the same as in previous iterations. They are generic enough to be used by either scheduler.

3.5. Example Job Service (MyJobService.java)
Your service methods will now use either @AbcQuartzRam or @AbcQuartzJdbc.

import org.springframework.stereotype.Component;

@Component
public class MyJobService {

    // This job will be handled by the RAM (in-memory) scheduler
    @AbcQuartzRam(cron = "0/10 * * * * ?", name = "DailyCleanupRamJob", disallowConcurrent = true)
    public void dailyCleanupRam() {
        System.out.println("🧹 RAM-based Daily cleanup executed! Current time: " + new java.util.Date());
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("🧹 RAM-based Daily cleanup finished! Current time: " + new java.util.Date());
    }

    // This job will be handled by the JDBC (persistent) scheduler
    @AbcQuartzJdbc(cron = "0/15 * * * * ?", name = "MonthlyReportJdbcJob", disallowConcurrent = false)
    public void monthlyReportJdbc() {
        System.out.println("📊 JDBC-based Monthly report executed! Current time: " + new java.util.Date());
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("📊 JDBC-based Monthly report finished! Current time: " + new java.util.Date());
    }

    // Another RAM-based job
    @AbcQuartzRam(cron = "0 0 9 * * ?", name = "AnotherRamJob", disallowConcurrent = true)
    public void anotherRamJob() {
        System.out.println("✨ RAM-based Another job executed! Current time: " + new java.util.Date());
    }

    // Another JDBC-based job
    @AbcQuartzJdbc(cron = "0 0 12 1 * ?", name = "AnotherJdbcJob", disallowConcurrent = true)
    public void anotherJdbcJob() {
        System.out.println("⏳ JDBC-based Another job executed! Current time: " + new java.util.Date());
    }
}

3.6. API Management (Optional)
If you have a Quartz management API (like the QuartzManagementService and QuartzManagementController discussed previously), you will need to adapt them to interact with both schedulers. This typically involves:

Autowiring both ramScheduler and jdbcScheduler into QuartzManagementService.

Modifying API methods to accept a parameter (e.g., schedulerType: "RAM" or "JDBC") to specify which scheduler to operate on.

Updating DTO mapping logic to correctly fetch job/trigger states from the specified scheduler.

4. Usage
To use this dual-scheduler setup:

Choose the appropriate annotation (@AbcQuartzRam or @AbcQuartzJdbc) for each method you want to schedule.

Ensure your QuartzConfig.java is correctly configured with both SchedulerFactoryBean beans and, crucially, the DataSource and PlatformTransactionManager for the JDBC scheduler.

Run the necessary Quartz DDL scripts on your database for the JDBC scheduler.

5. Advantages and Disadvantages of Dual Schedulers
Advantages:
Fine-grained Persistence Control: You can explicitly choose persistence for critical jobs while keeping less critical, high-frequency jobs in memory for performance.

Performance Optimization: RAM-based jobs avoid database overhead, which can be beneficial for very frequent or non-durable tasks.

Disadvantages:
Increased Complexity: Managing two separate scheduler instances adds significant overhead to your application's setup, configuration, and runtime monitoring.

Resource Duplication: You have two thread pools, potentially two sets of database connections (though a single DataSource can be shared), and increased memory usage.

Management Overhead: Your API and monitoring tools need to be aware of and distinguish between the two schedulers.

Potential for Confusion: Developers need to be careful to use the correct annotation.

6. Recommendation
While technically feasible, the dual-scheduler approach is rarely recommended for typical applications. For most use cases, a single Quartz Scheduler configured with JDBCJobStore provides the best balance of:

Reliability: All jobs are persistent and survive restarts.

Scalability: Supports clustering out of the box.

Simplicity: Easier to configure, manage, and monitor a single scheduler.

Consider this dual-scheduler setup only if you have a very specific and compelling performance requirement for certain non-critical, non-persistent jobs that cannot be met by a single persistent scheduler.

7. Dependencies (pom.xml)
Ensure you have the following dependencies in your pom.xml:

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId> </dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId> </dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-support</artifactId>
</dependency>
<dependency>
    <groupId>org.quartz-scheduler</groupId>
    <artifactId>quartz</artifactId>
    <version>2.3.2</version> </dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
</dependency>

<!--
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
-->
