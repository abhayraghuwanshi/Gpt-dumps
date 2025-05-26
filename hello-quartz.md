Okay, let's put all the pieces together for your robust, single, database-backed Quartz scheduler setup, using your custom `HikariDataSource` from an external config file and Spring Boot's auto-configuration.

---

### **1. `pom.xml` - Dependencies**

Ensure you have the necessary Spring Boot starters for web (optional, but common), JDBC, Quartz, and your database driver (e.g., PostgreSQL or H2), plus HikariCP if it's not brought in transitively.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version> <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>quartz-db-custom-ds-example</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>quartz-db-custom-ds-example</name>
    <description>Quartz with custom DataSource config</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId> </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-quartz</artifactId> </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId> </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### **2. `src/main/resources/db-config.properties` - Your External Database Configuration**

This is your custom configuration file that Spring Boot won't automatically read for `spring.datasource.*` properties.

```properties
db.url=jdbc:postgresql://localhost:5432/your_quartz_db
db.username=your_db_user
db.password=your_db_password
db.driver-class-name=org.postgresql.Driver
db.hikari.maxPoolSize=15
db.hikari.minIdle=3
db.hikari.connectionTimeout=30000
db.hikari.idleTimeout=600000
db.hikari.cachePrepStmts=true
db.hikari.prepStmtCacheSize=250
db.hikari.prepStmtCacheSqlLimit=2048
```

---

### **3. `src/main/resources/application.properties` - Quartz Specific Configuration**

This file now only contains properties specifically for Quartz's behavior and the database schema setup, not connection details.

```properties
# NO spring.datasource.* properties here anymore as they are in db-config.properties

# Quartz Configuration: Tell Spring Boot to use JDBC JobStore
spring.quartz.job-store-type=jdbc
spring.quartz.properties.org.quartz.scheduler.instanceName=MySpringBootQuartzScheduler
spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO
spring.quartz.properties.org.quartz.threadPool.threadCount=10
spring.quartz.properties.org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.PostgreSQLDelegate # IMPORTANT: Match your DB!
spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_
spring.quartz.properties.org.quartz.jobStore.isClustered=false # Set to true for clustering (requires proper setup)
# spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=20000 # For clustered mode, if isClustered=true

# To avoid auto-creating Quartz tables if you're managing them manually (recommended for production)
spring.quartz.jdbc.initialize-schema=never
```

---

### **4. `AbcQuartzJdbc.java` - The Custom Annotation**

This annotation defines the metadata for your Quartz jobs.

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AbcQuartzJdbc {
    String cron();
    String name() default "";
    boolean disallowConcurrent() default false; // false by default, allowing concurrent
}
```

---

### **5. `MethodInvokingJob.java` & `NonConcurrentMethodInvokingJob.java` - The Generic Job Classes**

These are helper classes that allow Quartz to invoke your annotated Spring bean methods.

**`MethodInvokingJob.java` (for jobs allowing concurrent execution):**
```java
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

// @PersistJobDataAfterExecution ensures JobDataMap changes are persisted,
// though for MethodInvokingJob, it's often not strictly needed unless
// you modify the map dynamically AND need those changes to persist.
@PersistJobDataAfterExecution
@Component
public class MethodInvokingJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(MethodInvokingJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Retrieve the Spring bean and method from JobDataMap
        Object bean = context.getJobDetail().getJobDataMap().get("bean");
        Method method = (Method) context.getJobDetail().getJobDataMap().get("method");

        if (bean == null || method == null) {
            logger.error("Failed to retrieve bean or method from JobDataMap for job: {}", context.getJobDetail().getKey());
            throw new JobExecutionException("Missing bean or method in JobDataMap");
        }

        try {
            logger.info("Executing job: {} - Method: {}.{}()", context.getJobDetail().getKey(), bean.getClass().getSimpleName(), method.getName());
            method.invoke(bean); // Invoke the actual method
            logger.info("Job {} execution completed successfully.", context.getJobDetail().getKey());
        } catch (Exception e) {
            logger.error("Error executing job: {} - Method: {}.{}()", context.getJobDetail().getKey(), bean.getClass().getSimpleName(), method.getName(), e);
            throw new JobExecutionException(e);
        }
    }
}
```

**`NonConcurrentMethodInvokingJob.java` (for jobs disallowing concurrent execution):**
```java
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

// This class extends the base MethodInvokingJob but adds the
// @DisallowConcurrentExecution annotation.
// Quartz uses this annotation to ensure only one instance of this job
// runs at a time.
@DisallowConcurrentExecution
@Component
public class NonConcurrentMethodInvokingJob extends MethodInvokingJob {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Just call the superclass's execute method.
        // The @DisallowConcurrentExecution annotation handles the concurrency logic.
        super.execute(context);
    }
}
```

---

### **6. `CustomDbAndQuartzConfig.java` - Your Bridge to Spring and Quartz**

This class is crucial. It reads your external `db-config.properties` and manually creates the `HikariDataSource`, `LocalContainerEntityManagerFactoryBean`, and `PlatformTransactionManager` beans that Spring Boot's Quartz auto-configuration will then discover and use.

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager; // For pure JDBC
import org.springframework.orm.jpa.JpaTransactionManager; // For JPA
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties; // For JPA properties

@Configuration
@EnableTransactionManagement // Enables Spring's declarative transaction management
@PropertySource("classpath:db-config.properties") // Load your custom DB config file
public class CustomDbAndQuartzConfig {

    // Inject properties from your custom db-config.properties
    @Value("${db.url}")
    private String dbUrl;
    @Value("${db.username}")
    private String dbUsername;
    @Value("${db.password}")
    private String dbPassword;
    @Value("${db.driver-class-name}")
    private String dbDriverClassName;

    // Hikari-specific properties
    @Value("${db.hikari.maxPoolSize:10}")
    private int maxPoolSize;
    @Value("${db.hikari.minIdle:2}")
    private int minIdle;
    @Value("${db.hikari.connectionTimeout:30000}")
    private long connectionTimeout;
    @Value("${db.hikari.idleTimeout:600000}")
    private long idleTimeout;
    @Value("${db.hikari.cachePrepStmts:true}")
    private boolean cachePrepStmts;
    @Value("${db.hikari.prepStmtCacheSize:250}")
    private int prepStmtCacheSize;
    @Value("${db.hikari.prepStmtCacheSqlLimit:2048}")
    private int prepStmtCacheSqlLimit;

    // 1. Define your custom HikariDataSource bean
    @Bean
    // @Primary // Uncomment if you have other DataSources and this is the main one
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName(dbDriverClassName);

        // Apply HikariCP specific properties
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.addDataSourceProperty("cachePrepStmts", String.valueOf(cachePrepStmts));
        config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(prepStmtCacheSize));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(prepStmtCacheSqlLimit));

        return new HikariDataSource(config);
    }

    // 2. Define your LocalContainerEntityManagerFactoryBean bean (if using JPA/Hibernate)
    // Quartz doesn't directly use this, but it's essential for your JPA setup,
    // and its presence influences the transaction manager type.
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.example.yourpackage.entities"); // <<< IMPORTANT: REPLACE WITH YOUR JPA ENTITY PACKAGE!

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "update"); // Or "none" in production for existing DB
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"); // <<< YOUR DATABASE DIALECT!
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(properties);

        return em;
    }

    // 3. Define your PlatformTransactionManager bean
    // Quartz requires this, and Spring Boot will find it automatically.
    // If you're using JPA, you typically use JpaTransactionManager.
    // If you were ONLY using JDBC, you would use DataSourceTransactionManager(dataSource).
    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}
```

---

### **7. `AbcQuartzMethodRegistrar.java` - Scans and Schedules Jobs**

This class remains simple, as it just autowires the single `Scheduler` bean provided by Spring Boot.

```java
import org.quartz.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Autowire the single Scheduler bean provided by Spring Boot's auto-configuration
    @Autowired
    private Scheduler scheduler;

    @PostConstruct
    public void init() throws Exception {
        logger.info("Starting scan for @AbcQuartzJdbc annotated methods...");

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);

        for (Object bean : beans.values()) {
            Method[] methods = bean.getClass().getDeclaredMethods();
            for (Method method : methods) {
                method.setAccessible(true);

                AbcQuartzJdbc jdbcAnnotation = method.getAnnotation(AbcQuartzJdbc.class);
                if (jdbcAnnotation != null) {
                    scheduleMethod(bean, method, jdbcAnnotation.cron(), jdbcAnnotation.name(), jdbcAnnotation.disallowConcurrent());
                }
            }
        }
        logger.info("Finished scanning and scheduling @AbcQuartzJdbc methods.");
    }

    private void scheduleMethod(Object bean, Method method, String cron, String name, boolean disallowConcurrent) throws SchedulerException {
        String jobName = name.isEmpty()
                ? bean.getClass().getSimpleName() + "." + method.getName()
                : name;

        Class<? extends Job> jobClass = disallowConcurrent
                ? NonConcurrentMethodInvokingJob.class
                : MethodInvokingJob.class;

        String jobGroup = "AbcQuartzJobGroup";
        String triggerGroup = "AbcQuartzTriggerGroup";

        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .usingJobData(new JobDataMap(Map.of(
                        "bean", bean,
                        "method", method
                )))
                .storeDurably() // Important for persistent jobs
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobName + "Trigger", triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .withMisfireHandlingInstructionDoNothing() // Choose appropriate misfire instruction
                )
                .build();

        if (scheduler.checkExists(jobDetail.getKey())) {
                            logger.info("Job '{}' already exists. Rescheduling...", jobName);
            scheduler.rescheduleJob(trigger.getKey(), trigger);
        } else {
            logger.info("Scheduling new job: '{}' with cron: '{}'", jobName, cron);
            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
```

---

### **8. `MyJobService.java` - Your Annotated Job Methods**

Your jobs will now use the single `@AbcQuartzJdbc` annotation.

```java
import org.springframework.stereotype.Component;

@Component
public class MyJobService {

    // This job runs every 15 seconds.
    @AbcQuartzJdbc(cron = "0/15 * * * * ?", name = "MyPersistentJob")
    public void myPersistentJob() {
        System.out.println("🚀 Persistent Job executed! Current time: " + new java.util.Date());
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("✅ Persistent Job finished! Current time: " + new java.util.Date());
    }

    // Another persistent job, running every minute and allowing concurrent executions
    @AbcQuartzJdbc(cron = "0 * * * * ?", name = "EveryMinuteJobConcurrent", disallowConcurrent = false)
    public void everyMinuteConcurrentJob() {
        System.out.println("⏱️ Every Minute Concurrent Job executed! Current time: " + new java.util.Date());
        try { Thread.sleep(20000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // Simulates a 20s task
        System.out.println("✔️ Every Minute Concurrent Job finished! Current time: " + new java.util.Date());
    }
}
```

---

### **Summary of the Solution and How it All Connects:**

1.  **External DB Config (`db-config.properties`):** You define your database connection details and HikariCP pool properties in a separate, non-Spring-standard properties file.
2.  **Custom Database & Quartz Config (`CustomDbAndQuartzConfig.java`):**
    * This `@Configuration` class is the **bridge**. It uses `@PropertySource` to load your `db-config.properties`.
    * It then manually constructs and exposes your `HikariDataSource` as a Spring `@Bean`, injecting values from your custom properties file using `@Value`.
    * Crucially, it also defines your `LocalContainerEntityManagerFactoryBean` (if using JPA) and your `PlatformTransactionManager` (likely `JpaTransactionManager`) as Spring `@Bean`s, ensuring they use this same `HikariDataSource`.
3.  **Spring Boot Quartz Auto-Configuration (`application.properties` & `spring-boot-starter-quartz`):**
    * With `spring.quartz.job-store-type=jdbc` in `application.properties`, Spring Boot knows you want a database-backed Quartz.
    * It then **automatically scans the Spring `ApplicationContext` for available `DataSource` and `PlatformTransactionManager` beans**. It finds the ones you explicitly created in `CustomDbAndQuartzConfig.java`.
    * Spring Boot uses these discovered beans to fully configure its default `SchedulerFactoryBean` and exposes a single `Scheduler` bean.
4.  **Custom Annotation & Registrar (`AbcQuartzJdbc.java`, `AbcQuartzMethodRegistrar.java`):**
    * Your `@AbcQuartzJdbc` annotation marks your job methods.
    * The `AbcQuartzMethodRegistrar` scans your Spring components for these annotations during application startup.
    * It then uses the *single `Scheduler` bean* (auto-configured by Spring Boot and backed by your custom `HikariDataSource`) to schedule your jobs into the database.
5.  **Job Execution (`MethodInvokingJob.java`, `MyJobService.java`):**
    * When Quartz triggers a job, it instantiates `MethodInvokingJob` (or `NonConcurrentMethodInvokingJob`).
    * This generic job reflects on your `MyJobService` instance to call the specific method you annotated.

This setup provides you with a robust, persistent Quartz scheduler using your custom database configuration, fully integrated with Spring Boot's powerful auto-configuration.
