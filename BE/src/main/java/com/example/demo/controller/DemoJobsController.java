package com.example.demo.controller;

import com.example.demo.config.SecurityConfig.Principal;
import com.example.demo.exception.ApiException;
import com.example.demo.service.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Profile("demo")
@RestController
public class DemoJobsController {
    private final CheckpointJobs jobs;
    private final HanareService service;
    public DemoJobsController(CheckpointJobs jobs,HanareService service) { this.jobs=jobs;this.service=service; }
    @PostMapping("/api/projects/{project}/demo-jobs")
    public Object run(@PathVariable UUID project,@RequestParam LocalDate date,@RequestParam String job,@AuthenticationPrincipal Principal p) {
        service.lead(project,p.id());
        if(!Set.of("remind","finalize").contains(job)) throw ApiException.invalid("Job must be remind or finalize.");
        return ApiController.ok(Map.of("created",jobs.runDemo(date,job.equals("finalize"),project)));
    }
}
