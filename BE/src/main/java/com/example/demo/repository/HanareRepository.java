package com.example.demo.repository;

import com.example.demo.config.SecurityConfig.Principal;
import com.example.demo.entity.*;
import static com.example.demo.dto.Views.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class HanareRepository {
    private final JdbcTemplate db;
    public HanareRepository(JdbcTemplate db) { this.db=db; }
    private static UUID uuid(ResultSet r,String c) throws SQLException { return r.getObject(c,UUID.class); }
    private static OffsetDateTime time(ResultSet r,String c) throws SQLException { return r.getObject(c,OffsetDateTime.class); }
    private static final String TASK_SQL="""
        SELECT t.*, u.name assignee_name, c.name confirmed_by_name FROM tasks t
        JOIN users u ON u.id=t.assignee_id LEFT JOIN users c ON c.id=t.confirmed_by
        """;
    private final RowMapper<TaskView> taskMapper=(r,n) -> new TaskView(uuid(r,"id"),uuid(r,"project_id"),
        uuid(r,"assignee_id"),r.getString("assignee_name"),r.getString("title"),r.getString("description"),
        r.getString("expected_output"),r.getObject("start_date",LocalDate.class),time(r,"deadline"),
        TaskStatus.valueOf(r.getString("status")),PlanningStatus.valueOf(r.getString("planning_status")),
        uuid(r,"confirmed_by"),r.getString("confirmed_by_name"),time(r,"confirmed_at"));
    private final RowMapper<ReportView> reportMapper=(r,n) -> new ReportView(uuid(r,"id"),uuid(r,"task_id"),
        r.getObject("checkpoint_date",LocalDate.class),TaskReportResult.valueOf(r.getString("result")),
        r.getString("note"),r.getString("evidence_url"),time(r,"reported_at"));
    private static final String SUPPORT_SQL="""
        SELECT s.*,t.title task_title,u.name requester_name FROM support_requests s
        JOIN tasks t ON t.id=s.task_id JOIN users u ON u.id=s.requested_by
        """;
    private final RowMapper<SupportView> supportMapper=(r,n) -> new SupportView(uuid(r,"id"),uuid(r,"task_id"),
        r.getString("task_title"),uuid(r,"requested_by"),r.getString("requester_name"),r.getString("reason"),
        r.getString("requested_help"),SupportRequestStatus.valueOf(r.getString("status")),r.getString("leader_note"),
        time(r,"created_at"),time(r,"resolved_at"));
    public Optional<Principal> login(String email) {
        return db.query("SELECT id,name,email,password_hash FROM users WHERE lower(email)=?",(r,n) ->
            new Principal(uuid(r,"id"),r.getString("name"),r.getString("email"),r.getString("password_hash")),email).stream().findFirst();
    }
    public List<ProjectView> projects(UUID user) {
        return db.query("SELECT p.*,m.role FROM projects p JOIN project_members m ON p.id=m.project_id WHERE m.user_id=? ORDER BY p.name",
            (r,n) -> new ProjectView(uuid(r,"id"),r.getString("name"),time(r,"deadline"),MemberRole.valueOf(r.getString("role"))),user);
    }
    public Optional<ProjectView> project(UUID id,UUID user) { return projects(user).stream().filter(p -> p.id().equals(id)).findFirst(); }
    public List<UUID> projectIds() { return db.query("SELECT id FROM projects ORDER BY id",(r,n) -> uuid(r,"id")); }
    public List<Person> members(UUID project) {
        return db.query("SELECT u.id,u.name,u.email,m.role FROM users u JOIN project_members m ON u.id=m.user_id WHERE m.project_id=? ORDER BY u.name",
            (r,n) -> new Person(uuid(r,"id"),r.getString("name"),r.getString("email"),MemberRole.valueOf(r.getString("role"))),project);
    }
    public List<TaskView> tasks(UUID project) { return db.query(TASK_SQL+" WHERE t.project_id=? ORDER BY t.deadline,t.id",taskMapper,project); }
    public Optional<TaskView> task(UUID id,boolean lock) {
        return db.query(TASK_SQL+" WHERE t.id=?"+(lock?" FOR UPDATE OF t":""),taskMapper,id).stream().findFirst();
    }
    public List<ReportView> reports(UUID task) { return db.query("SELECT * FROM task_reports WHERE task_id=? ORDER BY checkpoint_date DESC",reportMapper,task); }
    public List<ReportView> projectReports(UUID project,LocalDate friday) {
        return db.query("SELECT r.* FROM task_reports r JOIN tasks t ON r.task_id=t.id WHERE t.project_id=? AND r.checkpoint_date=?",reportMapper,project,friday);
    }
    public List<ReportView> previousReports(UUID project,UUID owner,LocalDate friday) {
        return db.query("SELECT DISTINCT ON (r.task_id) r.* FROM task_reports r JOIN tasks t ON r.task_id=t.id WHERE t.project_id=? AND t.assignee_id=? AND r.checkpoint_date<? ORDER BY r.task_id,r.checkpoint_date DESC",reportMapper,project,owner,friday);
    }
    public List<SupportView> support(UUID project) { return db.query(SUPPORT_SQL+" WHERE t.project_id=? ORDER BY s.created_at DESC",supportMapper,project); }
    public Optional<SupportView> supportById(UUID id) { return db.query(SUPPORT_SQL+" WHERE s.id=?",supportMapper,id).stream().findFirst(); }
    public void lockProject(UUID id) { db.queryForObject("SELECT id FROM projects WHERE id=? FOR UPDATE",UUID.class,id); }
    public void insertTask(UUID id,UUID project,UUID assignee,String title,String description,String output,LocalDate start,OffsetDateTime deadline) {
        db.update("INSERT INTO tasks(id,project_id,assignee_id,title,description,expected_output,start_date,deadline) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING",
            id,project,assignee,title,description,output,start,deadline);
    }
    public void plan(UUID id,String description,String output,LocalDate start,OffsetDateTime deadline) {
        db.update("UPDATE tasks SET description=?,expected_output=?,start_date=?,deadline=? WHERE id=?",description,output,start,deadline,id);
    }
    public void confirm(UUID id,UUID lead,OffsetDateTime now) { db.update("UPDATE tasks SET planning_status='CONFIRMED',confirmed_by=?,confirmed_at=? WHERE id=?",lead,now,id); }
    public void reopen(UUID id) { db.update("UPDATE tasks SET planning_status='DRAFT',confirmed_by=NULL,confirmed_at=NULL WHERE id=?",id); }
    public void status(UUID id,TaskStatus status) { db.update("UPDATE tasks SET status=? WHERE id=?",status.name(),id); }
    public void upsertReport(UUID task,LocalDate friday,TaskReportResult result,String note,String evidence,OffsetDateTime now) {
        db.update("""
            INSERT INTO task_reports(id,task_id,checkpoint_date,result,note,evidence_url,reported_at) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (task_id,checkpoint_date) DO UPDATE SET result=EXCLUDED.result,note=EXCLUDED.note,
            evidence_url=EXCLUDED.evidence_url,reported_at=EXCLUDED.reported_at
            """,UUID.randomUUID(),task,friday,result.name(),note,evidence,now);
    }
    public void insertSupport(UUID id,UUID task,UUID user,String reason,String help,OffsetDateTime now) {
        db.update("INSERT INTO support_requests(id,task_id,requested_by,reason,requested_help,created_at) VALUES (?,?,?,?,?,?)",id,task,user,reason,help,now);
    }
    public boolean resolve(UUID id,String note,OffsetDateTime now) {
        return db.update("UPDATE support_requests SET status='RESOLVED',leader_note=?,resolved_at=? WHERE id=? AND status='OPEN'",note,now,id)==1;
    }
    public boolean notificationExists(UUID recipient,UUID project,NotificationType type,LocalDate friday) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_id=? AND project_id=? AND type=? AND checkpoint_date=?)",
            Boolean.class,recipient,project,type.name(),friday));
    }
    public void notify(UUID recipient,UUID project,NotificationType type,String title,String message,LocalDate friday,OffsetDateTime now) {
        db.update("INSERT INTO notifications(id,recipient_id,project_id,type,title,message,checkpoint_date,created_at) VALUES (?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),recipient,project,type.name(),title.length()>200?title.substring(0,200):title,message,friday,now);
    }
    public List<NotificationView> notifications(UUID recipient) {
        return db.query("SELECT * FROM notifications WHERE recipient_id=? ORDER BY created_at DESC,id DESC LIMIT 20",(r,n) ->
            new NotificationView(uuid(r,"id"),uuid(r,"project_id"),NotificationType.valueOf(r.getString("type")),r.getString("title"),
                r.getString("message"),r.getObject("checkpoint_date",LocalDate.class),time(r,"read_at"),time(r,"created_at")),recipient);
    }
    public int unread(UUID recipient) { return db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=? AND read_at IS NULL",Integer.class,recipient); }
    public boolean markRead(UUID id,UUID user,OffsetDateTime now) {
        return db.update("UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND recipient_id=?",now,id,user)==1;
    }
}
