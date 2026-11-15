package com.example.demo.controller;

import com.example.demo.config.SecurityConfig.Principal;
import static com.example.demo.dto.Requests.*;
import com.example.demo.service.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final HanareService service;
    private final Environment environment;
    private final WeeklyPolicy weeks;
    public ApiController(HanareService service,Environment environment,WeeklyPolicy weeks) { this.service=service;this.environment=environment;this.weeks=weeks; }
    public static Map<String,Object> ok(Object data) { return Map.of("success",true,"data",data); }
    @GetMapping("/session")
    public Object session(@AuthenticationPrincipal Principal p,CsrfToken csrf) {
        return ok(Map.of("id",p.id(),"name",p.displayName(),"email",p.getUsername(),"csrfToken",csrf.getToken(),"csrfHeader",csrf.getHeaderName(),"serverNow",weeks.timestamp(),
            "demo",Arrays.asList(environment.getActiveProfiles()).contains("demo")));
    }
    @GetMapping("/projects") public Object projects(@AuthenticationPrincipal Principal p) { return ok(service.projects(p.id())); }
    @GetMapping("/projects/{project}/members") public Object members(@PathVariable UUID project,@AuthenticationPrincipal Principal p) { return ok(service.members(project,p.id())); }
    @GetMapping("/projects/{project}/dashboard")
    public Object dashboard(@PathVariable UUID project,@RequestParam(required=false) LocalDate checkpointDate,@AuthenticationPrincipal Principal p) { return ok(service.dashboard(project,p.id(),checkpointDate)); }
    @GetMapping("/projects/{project}/tasks")
    public Object tasks(@PathVariable UUID project,@RequestParam(defaultValue="mine") String scope,@AuthenticationPrincipal Principal p) { return ok(service.tasks(project,p.id(),scope)); }
    @PostMapping("/projects/{project}/tasks")
    public Object create(@PathVariable UUID project,@RequestBody CreateTask b,@AuthenticationPrincipal Principal p) {
        return ok(service.create(project,p.id(),b.assigneeId(),b.title(),b.description(),b.expectedOutput(),b.startDate(),b.deadline(),b.requestId()));
    }
    @GetMapping("/tasks/{id}") public Object detail(@PathVariable UUID id,@AuthenticationPrincipal Principal p) { return ok(service.detail(id,p.id())); }
    @PatchMapping("/tasks/{id}/plan") public Object plan(@PathVariable UUID id,@RequestBody Plan b,@AuthenticationPrincipal Principal p) {
        return ok(service.plan(id,p.id(),b.description(),b.expectedOutput(),b.startDate(),b.deadline()));
    }
    @PostMapping("/tasks/{id}/confirm") public Object confirm(@PathVariable UUID id,@AuthenticationPrincipal Principal p) { return ok(service.confirm(id,p.id())); }
    @PostMapping("/tasks/{id}/reopen") public Object reopen(@PathVariable UUID id,@AuthenticationPrincipal Principal p) { return ok(service.reopen(id,p.id())); }
    @PatchMapping("/tasks/{id}/status") public Object status(@PathVariable UUID id,@RequestBody Status b,@AuthenticationPrincipal Principal p) { return ok(service.status(id,p.id(),b.status(),b.rationale())); }
    @GetMapping("/projects/{project}/checkpoint")
    public Object checkpoint(@PathVariable UUID project,@RequestParam(required=false) LocalDate date,@AuthenticationPrincipal Principal p) { return ok(service.checkpoint(project,p.id(),date)); }
    @PutMapping("/tasks/{id}/reports/{date}") public Object report(@PathVariable UUID id,@PathVariable LocalDate date,@RequestBody Report b,@AuthenticationPrincipal Principal p) {
        return ok(service.report(id,p.id(),date,b.result(),b.note(),b.evidenceUrl()));
    }
    @PostMapping("/tasks/{id}/support-requests") public Object support(@PathVariable UUID id,@RequestBody Help b,@AuthenticationPrincipal Principal p) {
        return ok(Map.of("id",service.needHelp(id,p.id(),b.reason(),b.requestedHelp())));
    }
    @GetMapping("/projects/{project}/review") public Object review(@PathVariable UUID project,@RequestParam(required=false) LocalDate date,@AuthenticationPrincipal Principal p) { return ok(service.review(project,p.id(),date)); }
    @PatchMapping("/support-requests/{id}/resolve") public Object resolve(@PathVariable UUID id,@RequestBody Resolve b,@AuthenticationPrincipal Principal p) { service.resolve(id,p.id(),b.leaderNote());return ok(Map.of()); }
    @GetMapping("/notifications") public Object notifications(@AuthenticationPrincipal Principal p) { return ok(service.notifications(p.id())); }
    @PatchMapping("/notifications/{id}/read") public Object read(@PathVariable UUID id,@AuthenticationPrincipal Principal p) { service.markRead(id,p.id());return ok(Map.of()); }
}
