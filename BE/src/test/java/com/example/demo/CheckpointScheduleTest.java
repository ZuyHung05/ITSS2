package com.example.demo;

import com.example.demo.config.CheckpointSchedule;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static org.assertj.core.api.Assertions.assertThat;

class CheckpointScheduleTest {
    private ZonedDateTime next(String method,String utc) throws Exception {
        var schedule=CheckpointSchedule.class.getMethod(method).getAnnotation(Scheduled.class);
        var zone=ZoneId.of(schedule.zone());
        return CronExpression.parse(schedule.cron()).next(Instant.parse(utc).atZone(zone));
    }
    @Test void realReminderCronFiresFriday1530VietnamAndThenNextFriday() throws Exception {
        assertThat(next("reminder","2026-11-20T08:29:00Z").toInstant()).isEqualTo(Instant.parse("2026-11-20T08:30:00Z"));
        assertThat(next("reminder","2026-11-20T08:30:00Z").toInstant()).isEqualTo(Instant.parse("2026-11-27T08:30:00Z"));
    }
    @Test void realFinalizationCronFiresFriday2200VietnamAndThenNextFriday() throws Exception {
        assertThat(next("finalizeCheckpoint","2026-11-20T14:59:00Z").toInstant()).isEqualTo(Instant.parse("2026-11-20T15:00:00Z"));
        assertThat(next("finalizeCheckpoint","2026-11-20T15:00:00Z").toInstant()).isEqualTo(Instant.parse("2026-11-27T15:00:00Z"));
    }
}
