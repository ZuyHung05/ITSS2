package com.example.demo.config;

import com.example.demo.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="hanare.jobs.enabled",havingValue="true",matchIfMissing=true)
public class CheckpointSchedule {
    private final CheckpointJobs jobs;
    private final WeeklyPolicy weeks;
    public CheckpointSchedule(CheckpointJobs jobs,WeeklyPolicy weeks) { this.jobs=jobs;this.weeks=weeks; }
    @Scheduled(cron="0 30 15 * * FRI",zone="Asia/Ho_Chi_Minh")
    public void reminder() { jobs.run(weeks.currentFriday(),false,null); }
    @Scheduled(cron="0 0 22 * * FRI",zone="Asia/Ho_Chi_Minh")
    public void finalizeCheckpoint() { jobs.run(weeks.currentFriday(),true,null); }
}
