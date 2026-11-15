package com.example.demo;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Isolated localhost fixture for browser checks. Never connects to Supabase. */
public class BrowserDemo {
    @TestConfiguration(proxyBeanMethods=false)
    public static class TimeConfig {
        @Bean @Primary Clock browserClock() { return Clock.fixed(Instant.parse("2026-11-13T11:00:00Z"),ZoneOffset.UTC); }
    }
    public static void main(String[] args) throws Exception {
        var pg=EmbeddedPostgres.builder().setPort(0).start();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try {pg.close();} catch(Exception ignored) {}}));
        new SpringApplicationBuilder(DemoApplication.class,TimeConfig.class).profiles("demo").run(
            "--spring.datasource.url="+pg.getJdbcUrl("postgres","postgres"),"--spring.datasource.username=postgres","--spring.datasource.password=",
            "--server.port=8082","--server.address=127.0.0.1","--hanare.jobs.enabled=false","--spring.config.import=",
            "--hanare.demo.password=Browser-demo-test-only-2026","--logging.level.root=WARN","--logging.level.org.springframework=WARN");
    }
}
