package com.example.demo.service;

import static com.example.demo.dto.Views.*;
import com.example.demo.entity.*;
import com.example.demo.repository.HanareRepository;
import com.example.demo.exception.ApiException;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HanareService {
    private final HanareRepository repo;
    private final WeeklyPolicy weeks;
    public HanareService(HanareRepository repo,WeeklyPolicy weeks) { this.repo=repo;this.weeks=weeks; }
    public List<ProjectView> projects(UUID user) { return repo.projects(user); }
    public ProjectView project(UUID project,UUID user) { return repo.project(project,user).orElseThrow(ApiException::forbidden); }
    public ProjectView lead(UUID project,UUID user) {
        var p=project(project,user);if(p.role()!=MemberRole.LEAD) throw ApiException.forbidden();return p;
    }
    public List<Person> members(UUID project,UUID user) { project(project,user);return repo.members(project); }
    public List<TaskView> tasks(UUID project,UUID user,String scope) {
        var p=project(project,user);
        if(!Set.of("mine","team").contains(scope)) throw ApiException.invalid("Task scope must be mine or team.");
        if(scope.equals("team")&&p.role()!=MemberRole.LEAD) throw ApiException.forbidden();
        return repo.tasks(project).stream().filter(t -> scope.equals("team")||t.assigneeId().equals(user)).toList();
    }
    private TaskView task(UUID id,UUID user,boolean lock) {
        var t=repo.task(id,lock).orElseThrow(ApiException::missing);project(t.projectId(),user);return t;
    }
    public TaskDetail detail(UUID id,UUID user) {
        var t=task(id,user,false);var p=project(t.projectId(),user);boolean owner=t.assigneeId().equals(user);
        boolean privateAccess=owner||p.role()==MemberRole.LEAD;
        return new TaskDetail(t,p.role(),owner,privateAccess?repo.reports(id):List.of(),privateAccess?
            repo.support(t.projectId()).stream().filter(s -> s.taskId().equals(id)&&(p.role()==MemberRole.LEAD||s.requestedBy().equals(user))).toList():List.of());
    }
    private static String text(String value,String field,int max,boolean required) {
        var result=value==null?null:value.strip();
        if(required&&(result==null||result.isEmpty())) throw ApiException.invalid(field+" is required.");
        if(result!=null&&result.length()>max) throw ApiException.invalid(field+" must be at most "+max+" characters.");
        return result;
    }
    private void dates(LocalDate start,OffsetDateTime deadline) {
        if(start==null||deadline==null) throw ApiException.invalid("Start date and deadline are required.");
        if(deadline.toInstant().isBefore(start.atStartOfDay(WeeklyPolicy.ZONE).toInstant())) throw ApiException.invalid("Deadline must be on or after the start date.");
    }
    @Transactional
    public TaskView create(UUID project,UUID user,UUID assignee,String title,String description,String output,LocalDate start,OffsetDateTime deadline) {
        return create(project,user,assignee,title,description,output,start,deadline,null);
    }
    @Transactional
    public TaskView create(UUID project,UUID user,UUID assignee,String title,String description,String output,LocalDate start,OffsetDateTime deadline,UUID requestId) {
        lead(project,user);dates(start,deadline);
        if(assignee==null||repo.members(project).stream().noneMatch(m -> m.id().equals(assignee))) throw ApiException.invalid("Assignee must belong to this project.");
        var safeTitle=text(title,"Title",200,true);
        var safeDescription=text(description,"Description",10000,false);
        var safeOutput=text(output,"Expected output",10000,true);
        var id=requestId==null?UUID.randomUUID():requestId;
        repo.insertTask(id,project,assignee,safeTitle,safeDescription,safeOutput,start,deadline);
        var saved=repo.task(id,false).orElseThrow();
        if(!saved.projectId().equals(project)||!saved.assigneeId().equals(assignee)
            ||!saved.title().equals(safeTitle)||!Objects.equals(saved.description(),safeDescription)
            ||!Objects.equals(saved.expectedOutput(),safeOutput)||!Objects.equals(saved.startDate(),start)
            ||!saved.deadline().isEqual(deadline))
            throw ApiException.conflict("This creation request ID has already been used for a different task. Refresh and try again.");
        return saved;
    }
    @Transactional
    public TaskView plan(UUID id,UUID user,String description,String output,LocalDate start,OffsetDateTime deadline) {
        var t=task(id,user,true);
        if(!t.assigneeId().equals(user)&&project(t.projectId(),user).role()!=MemberRole.LEAD) throw ApiException.forbidden();
        if(t.planningStatus()!=PlanningStatus.DRAFT) throw ApiException.conflict("Confirmed plans are locked. The TL must reopen the draft first.");
        dates(start,deadline);repo.plan(id,text(description,"Description",10000,false),text(output,"Expected output",10000,true),start,deadline);
        return repo.task(id,false).orElseThrow();
    }
    @Transactional
    public TaskView confirm(UUID id,UUID user) {
        var t=task(id,user,true);lead(t.projectId(),user);
        if(t.planningStatus()!=PlanningStatus.DRAFT) throw ApiException.conflict("This plan is already confirmed.");
        dates(t.startDate(),t.deadline());text(t.expectedOutput(),"Expected output",10000,true);
        repo.confirm(id,user,weeks.timestamp());return repo.task(id,false).orElseThrow();
    }
    @Transactional
    public TaskView reopen(UUID id,UUID user) {
        var t=task(id,user,true);lead(t.projectId(),user);
        if(t.planningStatus()!=PlanningStatus.CONFIRMED) throw ApiException.conflict("This plan is already a draft.");
        repo.reopen(id);return repo.task(id,false).orElseThrow();
    }
    @Transactional
    public TaskView status(UUID id,UUID user,TaskStatus status,String rationale) {
        var t=task(id,user,true);boolean own=t.assigneeId().equals(user);
        if(!own) lead(t.projectId(),user);
        if(t.planningStatus()!=PlanningStatus.CONFIRMED) throw ApiException.conflict("Confirm the plan before changing execution status.");
        if(status==null) throw ApiException.invalid("Status is required.");
        if(!own) {
            var reason=text(rationale,"Rationale for TL status correction",2000,true);
            repo.notify(t.assigneeId(),t.projectId(),NotificationType.HELP_UPDATE,"Task status updated by TL",
                t.title()+" → "+status+". Reason: "+reason,null,weeks.timestamp());
        }
        repo.status(id,status);return repo.task(id,false).orElseThrow();
    }
    private Map<UUID,ReportView> reportMap(UUID project,LocalDate date) {
        return repo.projectReports(project,date).stream().collect(Collectors.toMap(ReportView::taskId,Function.identity()));
    }
    @Transactional(readOnly=true)
    public Dashboard dashboard(UUID project,UUID user,LocalDate requested) {
        var p=project(project,user);var date=weeks.friday(requested);var tasks=repo.tasks(project);var reports=reportMap(project,date);
        var summaries=repo.members(project).stream().map(m -> weeks.summarize(m,tasks,reports,date,p.role()==MemberRole.LEAD||m.id().equals(user))).toList();
        var mine=tasks.stream().filter(t -> t.assigneeId().equals(user)).sorted(Comparator.comparing((TaskView t) -> !weeks.overdue(t)).thenComparing(TaskView::deadline)).toList();
        int support=p.role()==MemberRole.LEAD?(int)repo.support(project).stream().filter(s -> s.status()==SupportRequestStatus.OPEN).count():0;
        int issues=summaries.stream().flatMap(m -> m.tasks().stream()).mapToInt(t -> t.reportRequired()&&(t.report()==null||t.report().result()==TaskReportResult.NOT_ACHIEVED)?1:0).sum();
        return new Dashboard(p,date,weeks.cutoff(date),weeks.closed(date),!date.equals(weeks.currentFriday()),summaries,mine,support,issues);
    }
    @Transactional(readOnly=true)
    public CheckpointView checkpoint(UUID project,UUID user,LocalDate requested) {
        var p=project(project,user);var date=weeks.friday(requested);var reports=reportMap(project,date);
        var rows=repo.tasks(project).stream().filter(t -> t.assigneeId().equals(user)&&weeks.due(t,date))
            .map(t -> new WeeklyTask(t,reports.get(t.id()),weeks.overdue(t),t.status()!=TaskStatus.DONE)).toList();
        var previous=repo.previousReports(project,user,date).stream().collect(Collectors.toMap(ReportView::taskId,Function.identity()));
        return new CheckpointView(p,date,weeks.cutoff(date),weeks.window(date),rows.stream().filter(WeeklyTask::reportRequired).toList(),rows.stream().filter(t -> !t.reportRequired()).toList(),previous);
    }
    @Transactional
    public List<ReportView> report(UUID id,UUID user,LocalDate requested,TaskReportResult result,String note,String evidence) {
        var t=task(id,user,true);var date=weeks.friday(requested);
        if(!t.assigneeId().equals(user)) throw ApiException.forbidden();
        if(!weeks.window(date).equals("OPEN")) throw ApiException.conflict("Reports can only be saved on this Friday before 22:00 Vietnam time.");
        if(!weeks.due(t,date)||t.status()==TaskStatus.DONE) throw ApiException.conflict("This task does not require a report for the selected checkpoint.");
        if(result==null) throw ApiException.invalid("Checkpoint result is required.");
        var safeNote=text(note,"Note",5000,result==TaskReportResult.NOT_ACHIEVED);
        var safeUrl=text(evidence,"Evidence URL",2000,false);
        if(safeUrl!=null&&!safeUrl.isEmpty()) {
            try { var uri=URI.create(safeUrl);if(uri.getScheme()==null||!Set.of("https","http").contains(uri.getScheme())||uri.getHost()==null) throw new IllegalArgumentException(); }
            catch(IllegalArgumentException ex) { throw ApiException.invalid("Evidence URL must be an http or https URL."); }
        }
        repo.upsertReport(id,date,result,safeNote,safeUrl,weeks.timestamp());return repo.reports(id);
    }
    @Transactional
    public UUID needHelp(UUID taskId,UUID user,String reason,String help) {
        var t=task(taskId,user,true);if(!t.assigneeId().equals(user)) throw ApiException.forbidden();
        var safeReason=text(reason,"Reason",5000,true);var safeHelp=text(help,"Requested help",5000,false);
        var id=UUID.randomUUID();repo.insertSupport(id,taskId,user,safeReason,safeHelp,weeks.timestamp());
        for(var m:repo.members(t.projectId())) if(m.role()==MemberRole.LEAD)
            repo.notify(m.id(),t.projectId(),NotificationType.NEED_HELP,"Need Help: "+t.title(),t.assigneeName()+" has asked for TL support. Open Team Review to see the private request.",null,weeks.timestamp());
        return id;
    }
    @Transactional(readOnly=true)
    public ReviewView review(UUID project,UUID user,LocalDate date) {
        lead(project,user);return new ReviewView(dashboard(project,user,date),repo.tasks(project).stream().filter(t -> t.planningStatus()==PlanningStatus.DRAFT).toList(),repo.support(project));
    }
    @Transactional
    public void resolve(UUID id,UUID user,String note) {
        var s=repo.supportById(id).orElseThrow(ApiException::missing);var t=task(s.taskId(),user,true);lead(t.projectId(),user);
        var safeNote=text(note,"Leader note",5000,false);
        if(!repo.resolve(id,safeNote,weeks.timestamp())) throw ApiException.conflict("This support request is already resolved.");
        repo.notify(s.requestedBy(),t.projectId(),NotificationType.HELP_UPDATE,"Need Help resolved",t.title()+": "+(safeNote==null||safeNote.isBlank()?"The TL marked your request resolved.":safeNote),null,weeks.timestamp());
    }
    public Map<String,Object> notifications(UUID user) { return Map.of("items",repo.notifications(user),"unread",repo.unread(user)); }
    @Transactional
    public void markRead(UUID id,UUID user) { if(!repo.markRead(id,user,weeks.timestamp())) throw ApiException.missing(); }
}
