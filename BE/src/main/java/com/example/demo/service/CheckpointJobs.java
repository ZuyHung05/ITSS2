package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.HanareRepository;
import static com.example.demo.dto.Views.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckpointJobs {
    private final HanareRepository repo;
    private final WeeklyPolicy weeks;
    public CheckpointJobs(HanareRepository repo,WeeklyPolicy weeks) { this.repo=repo;this.weeks=weeks; }
    @Transactional
    public int run(LocalDate requested,boolean finalize,UUID onlyProject) {
        return execute(requested,finalize,onlyProject,false);
    }
    /** Explicit, authenticated classroom simulation; production scheduling uses run(). */
    @Transactional
    public int runDemo(LocalDate requested,boolean finalize,UUID onlyProject) {
        return execute(requested,finalize,onlyProject,true);
    }
    private boolean withinWindow(LocalDate date,boolean finalize) {
        var now=weeks.now();
        if(!now.toLocalDate().equals(date)) return false;
        var time=now.toLocalTime();
        return finalize?!time.isBefore(LocalTime.of(22,0)):
            !time.isBefore(LocalTime.of(15,30))&&time.isBefore(LocalTime.of(22,0));
    }
    private int execute(LocalDate requested,boolean finalize,UUID onlyProject,boolean demo) {
        var date=weeks.friday(requested);int created=0;
        if(!demo&&!withinWindow(date,finalize)) return 0;
        var projectIds=onlyProject==null?repo.projectIds():List.of(onlyProject);
        for(var project:projectIds) {
            // All instances take the same project row lock before the existence check and insert.
            // Keep these operations in this transaction; a process-local synchronized block is insufficient.
            repo.lockProject(project);
            // A lock wait can cross the time boundary; check the server clock again.
            if(!demo&&!withinWindow(date,finalize)) continue;
            var tasks=repo.tasks(project);
            var reports=repo.projectReports(project,date).stream().collect(Collectors.toMap(ReportView::taskId,Function.identity()));
            var members=repo.members(project);
            var required=tasks.stream().filter(t -> weeks.due(t,date)&&t.status()!=TaskStatus.DONE).toList();
            if(finalize) {
                long missing=required.stream().filter(t -> !reports.containsKey(t.id())).count();
                long failed=required.stream().filter(t -> reports.containsKey(t.id())&&reports.get(t.id()).result()==TaskReportResult.NOT_ACHIEVED).count();
                if(missing+failed>0) for(var member:members) if(member.role()==MemberRole.LEAD)
                    created+=ensure(member.id(),project,NotificationType.CHECKPOINT_ALERT,date,"Friday checkpoint needs review",
                        missing+" missing report(s), "+failed+" NOT_ACHIEVED report(s). These are reporting issues; check actual task deadlines separately.");
            } else {
                for(var member:members) {
                    long missing=required.stream().filter(t -> t.assigneeId().equals(member.id())&&!reports.containsKey(t.id())).count();
                    if(missing>0) created+=ensure(member.id(),project,NotificationType.CHECKPOINT_REMINDER,date,"Friday checkpoint reminder",
                        missing+" unfinished due task(s) still need your report before 22:00 Vietnam time.");
                }
            }
        }
        return created;
    }
    private int ensure(UUID recipient,UUID project,NotificationType type,LocalDate friday,String title,String message) {
        if(repo.notificationExists(recipient,project,type,friday)) return 0;
        repo.notify(recipient,project,type,title,message,friday,weeks.timestamp());return 1;
    }
}
