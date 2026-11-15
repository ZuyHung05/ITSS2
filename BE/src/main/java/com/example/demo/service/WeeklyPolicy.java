package com.example.demo.service;

import static com.example.demo.dto.Views.*;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WeeklyPolicy {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;
    public WeeklyPolicy(Clock clock) { this.clock=clock; }
    public ZonedDateTime now() { return ZonedDateTime.now(clock).withZoneSameInstant(ZONE); }
    public OffsetDateTime timestamp() { return now().toOffsetDateTime(); }
    public LocalDate currentFriday() { return now().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(4); }
    public LocalDate friday(LocalDate requested) {
        var date=requested==null?currentFriday():requested;
        if(date.getDayOfWeek()!=DayOfWeek.FRIDAY) throw ApiException.invalid("Checkpoint date must be a Friday (Vietnam time).");
        return date;
    }
    public OffsetDateTime cutoff(LocalDate date) { return friday(date).atTime(22,0).atZone(ZONE).toOffsetDateTime(); }
    public boolean closed(LocalDate date) { return !now().toInstant().isBefore(cutoff(date).toInstant()); }
    public String window(LocalDate date) {
        if(closed(date)) return "CLOSED";
        return now().toLocalDate().equals(date)?"OPEN":"NOT_YET_DUE";
    }
    public boolean due(TaskView task,LocalDate date) {
        if(task.planningStatus()!=PlanningStatus.CONFIRMED) return false;
        var monday=date.minusDays(4).atStartOfDay(ZONE).toInstant();
        var nextMonday=date.plusDays(3).atStartOfDay(ZONE).toInstant();
        var deadline=task.deadline().toInstant();
        return (!deadline.isBefore(monday)&&deadline.isBefore(nextMonday))
            || (deadline.isBefore(monday)&&task.status()!=TaskStatus.DONE);
    }
    public boolean overdue(TaskView task) { return task.status()!=TaskStatus.DONE&&task.deadline().toInstant().isBefore(now().toInstant()); }
    public MemberSummary summarize(Person member,List<TaskView> tasks,Map<UUID,ReportView> reports,LocalDate date,boolean details) {
        var due=tasks.stream().filter(t -> t.assigneeId().equals(member.id())&&due(t,date)).toList();
        int completed=0,reported=0,achieved=0,failed=0,missing=0;
        boolean allSuccess=true;
        var rows=new ArrayList<WeeklyTask>();
        for(var task:due) {
            var report=reports.get(task.id());
            boolean done=task.status()==TaskStatus.DONE;
            if(done) completed++;
            if(report!=null) { reported++;if(report.result()==TaskReportResult.ACHIEVED) achieved++; else failed++; }
            if(!done&&report==null) missing++;
            if(!done&&(report==null||report.result()!=TaskReportResult.ACHIEVED)) allSuccess=false;
            var publicReport=report==null||details?report:new ReportView(report.id(),report.taskId(),report.checkpointDate(),report.result(),null,null,report.reportedAt());
            rows.add(new WeeklyTask(task,publicReport,overdue(task),!done));
        }
        String state=due.isEmpty()?"GRAY":!closed(date)?"PENDING":allSuccess?"GREEN":completed==0&&reported==0?"RED":"YELLOW";
        String explanation=switch(state) {
            case "GRAY" -> "No weekly due tasks.";
            case "PENDING" -> "Live / awaiting Friday 22:00 checkpoint.";
            case "GREEN" -> "Every due task is completed or self-reported ACHIEVED.";
            case "RED" -> "No completed tasks and no weekly reports in this cohort.";
            default -> "Some work is completed or reported; other required reports are missing or NOT_ACHIEVED.";
        };
        return new MemberSummary(member,due.size(),completed,reported,achieved,failed,missing,state,explanation,rows);
    }
}
