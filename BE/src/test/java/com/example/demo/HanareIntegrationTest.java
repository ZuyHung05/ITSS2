package com.example.demo;

import com.example.demo.config.SecurityConfig.Principal;
import com.example.demo.entity.*;
import com.example.demo.service.*;
import com.example.demo.exception.ApiException;
import static com.example.demo.dto.Views.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"hanare.jobs.enabled=false","spring.config.import=","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HanareIntegrationTest.TimeConfig.class)
class HanareIntegrationTest {
    static final EmbeddedPostgres PG=startPostgres();
    static EmbeddedPostgres startPostgres() { try { return EmbeddedPostgres.builder().setPort(0).start(); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->PG.getJdbcUrl("postgres","postgres"));
        p.add("spring.datasource.username",()->"postgres");p.add("spring.datasource.password",()->"");
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock testClock() { return new MutableClock(); } }
    @Autowired JdbcTemplate db;
    @Autowired HanareService service;
    @Autowired CheckpointJobs jobs;
    @Autowired MutableClock clock;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    final UUID lead=UUID.fromString("00000000-0000-0000-0000-000000000001");
    final UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000002");
    final UUID peer=UUID.fromString("00000000-0000-0000-0000-000000000003");
    final UUID outsider=UUID.fromString("00000000-0000-0000-0000-000000000004");
    final UUID project=UUID.fromString("10000000-0000-0000-0000-000000000001");
    final UUID otherProject=UUID.fromString("10000000-0000-0000-0000-000000000002");
    final LocalDate friday=LocalDate.of(2026,11,13);
    static String hash;
    @BeforeEach void setup() {
        clock.set("2026-11-13T08:30:00Z");
        db.execute("TRUNCATE projects, users CASCADE");
        if(hash==null) hash=passwords.encode("Test-only-password-2026");
        seedUser(lead,"Lead");seedUser(owner,"Owner");seedUser(peer,"Peer");seedUser(outsider,"Outsider");
        db.update("INSERT INTO projects(id,name) VALUES (?,?),(?,?)",project,"Test Project",otherProject,"Other Project");
        db.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'LEAD'),(?,?,'MEMBER'),(?,?,'MEMBER'),(?,?,'LEAD')",project,lead,project,owner,project,peer,otherProject,outsider);
    }
    void seedUser(UUID id,String name) { db.update("INSERT INTO users(id,name,email,password_hash) VALUES (?,?,?,?)",id,name,name.toLowerCase()+"@test.invalid",hash); }
    Principal principal(UUID id,String name) { return new Principal(id,name,name.toLowerCase()+"@test.invalid",hash); }
    OffsetDateTime due(int days) { return friday.plusDays(days).atTime(23,0).atZone(WeeklyPolicy.ZONE).toOffsetDateTime(); }
    TaskView task(UUID assignee,int days,boolean confirmed) {
        var t=service.create(project,lead,assignee,"Implement task","Scope","Deliverable",friday.minusDays(14),due(days));
        return confirmed?service.confirm(t.id(),lead):t;
    }
    void statusError(Runnable action,int status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(status)); }
    List<Integer> race(Callable<Integer> a,Callable<Integer> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try { var futures=pool.invokeAll(List.of(a,b));return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS)); }
        finally {pool.shutdownNow();}
    }
    @Test void emptyDatabaseMigrationHasTenBusinessTablesAndVietnamDateCheck() {
        var count=db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'",Integer.class);
        assertThat(count).isEqualTo(10);
        assertThat(db.queryForObject("SELECT success FROM flyway_schema_history WHERE version='1'",Boolean.class)).isTrue();
        var early=friday.atTime(0,30).atZone(WeeklyPolicy.ZONE).toOffsetDateTime();
        assertThat(service.create(project,lead,owner,"Early deadline","Scope","Output",friday,early).deadline()).isEqualTo(early);
    }
    @Test void sessionLoginCsrfAuthorizationAndPasswordPrivacy() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        mvc.perform(formLogin("/login").user("email","owner@test.invalid").password("Test-only-password-2026")).andExpect(authenticated());
        mvc.perform(formLogin("/login").user("email","owner@test.invalid").password("wrong")).andExpect(unauthenticated());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/projects/"+project+"/tasks").with(user(principal(owner,"Owner")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/"+otherProject+"/dashboard").with(user(principal(owner,"Owner")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/"+project+"/review").with(user(principal(owner,"Owner")))).andExpect(status().isForbidden());
        mvc.perform(get("/projects/"+project+"/review").with(user(principal(owner,"Owner")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/"+project+"/dashboard").with(user(principal(owner,"Owner"))))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
        mvc.perform(get("/api/session").with(user(principal(owner,"Owner")))).andExpect(jsonPath("$.data.id").value(owner.toString())).andExpect(jsonPath("$.data.csrfToken").isNotEmpty());
        mvc.perform(post("/api/projects/"+project+"/demo-jobs?date="+friday+"&job=remind").with(user(principal(lead,"Lead"))).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void draftOwnershipConfirmationAndReopenAreEnforced() {
        statusError(()->service.create(project,lead,outsider,"Task","Scope","Out",friday,due(0)),400);
        statusError(()->service.create(project,owner,owner,"Task","Scope","Out",friday,due(0)),403);
        var t=task(owner,0,false);
        statusError(()->service.plan(t.id(),peer,"Scope","Output",friday,due(0)),403);
        var proposal=service.plan(t.id(),owner,"Changed scope","New output",friday,due(2));
        assertThat(proposal.planningStatus()).isEqualTo(PlanningStatus.DRAFT);
        statusError(()->service.confirm(t.id(),owner),403);
        var confirmed=service.confirm(t.id(),lead);
        assertThat(confirmed.confirmedBy()).isEqualTo(lead);assertThat(confirmed.confirmedAt()).isNotNull();
        statusError(()->service.plan(t.id(),owner,"Scope","Output",friday,due(0)),409);
        statusError(()->service.plan(t.id(),lead,"Scope","Output",friday,due(0)),409);
        assertThat(service.status(t.id(),owner,TaskStatus.DONE,null).deadline()).isEqualTo(proposal.deadline());
        assertThat(db.queryForObject("SELECT count(*) FROM task_reports",Integer.class)).isZero();
        var draft=service.reopen(t.id(),lead);assertThat(draft.confirmedBy()).isNull();assertThat(draft.confirmedAt()).isNull();
        statusError(()->service.status(t.id(),owner,TaskStatus.IN_PROGRESS,null),409);
        service.plan(t.id(),owner,"Scope","Revised",friday,due(0));service.confirm(t.id(),lead);
        statusError(()->service.detail(t.id(),outsider),403);
    }
    @Test void reportsValidateScopeNotesAndCutoffAndUpsert() {
        var t=task(owner,2,true);
        var future=task(owner,7,true);
        var overdue=task(owner,-7,true);
        assertThat(service.checkpoint(project,owner,friday).tasks()).extracting(w->w.task().id()).contains(t.id(),overdue.id()).doesNotContain(future.id());
        statusError(()->service.report(t.id(),peer,friday,TaskReportResult.ACHIEVED,"Ready",null),403);
        statusError(()->service.report(future.id(),owner,friday,TaskReportResult.ACHIEVED,"Ready",null),409);
        statusError(()->service.report(t.id(),owner,friday,TaskReportResult.NOT_ACHIEVED," ",null),400);
        statusError(()->service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"Ready","javascript:alert(1)"),400);
        statusError(()->service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"Ready","relative-url"),400);
        service.report(t.id(),owner,friday,TaskReportResult.NOT_ACHIEVED,"Still working",null);
        clock.set("2026-11-13T14:59:59Z");service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"Now ready","https://example.com/result");
        assertThat(db.queryForObject("SELECT count(*) FROM task_reports WHERE task_id=?",Integer.class,t.id())).isEqualTo(1);
        assertThat(service.detail(t.id(),owner).task().status()).isEqualTo(TaskStatus.TODO);
        assertThat(service.checkpoint(project,owner,friday).tasks().stream().filter(w->w.task().id().equals(t.id())).findFirst().orElseThrow().overdue()).isFalse();
        clock.set("2026-11-13T15:00:00Z");statusError(()->service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"Late",null),409);
        clock.set("2026-11-12T15:00:00Z");statusError(()->service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"Early",null),409);
        clock.set("2026-11-20T08:30:00Z");
        var next=service.checkpoint(project,owner,friday.plusWeeks(1));
        assertThat(next.tasks()).extracting(w->w.task().id()).contains(t.id());
        assertThat(next.previousReports().get(t.id()).result()).isEqualTo(TaskReportResult.ACHIEVED);
        service.report(t.id(),owner,friday.plusWeeks(1),TaskReportResult.NOT_ACHIEVED,"Still unfinished",null);
        assertThat(db.queryForObject("SELECT count(*) FROM task_reports WHERE task_id=?",Integer.class,t.id())).isEqualTo(2);
    }
    @Test void concurrentReportCorrectionsHaveOneRow() throws Exception {
        var t=task(owner,0,true);
        var results=race(()->{service.report(t.id(),owner,friday,TaskReportResult.ACHIEVED,"One",null);return 1;},()->{service.report(t.id(),owner,friday,TaskReportResult.NOT_ACHIEVED,"Two",null);return 1;});
        assertThat(results).containsExactly(1,1);
        assertThat(db.queryForObject("SELECT count(*) FROM task_reports WHERE task_id=? AND checkpoint_date=?",Integer.class,t.id(),friday)).isEqualTo(1);
    }
    @Test void needHelpPrivacyAndLeadResolutionAreTransactional() throws Exception {
        var t=service.create(project,lead,owner,"X".repeat(200),"Scope","Output",friday,due(7));
        var id=service.needHelp(t.id(),owner,"Private blocker detail","Pair with me");
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE type='NEED_HELP' AND recipient_id=?",Integer.class,lead)).isEqualTo(1);
        statusError(()->service.needHelp(t.id(),peer,"Other person's task",null),403);
        statusError(()->service.resolve(id,owner,"Self-resolve"),403);
        assertThat(service.detail(t.id(),peer).supportRequests()).isEmpty();
        mvc.perform(get("/api/projects/"+project+"/dashboard").with(user(principal(peer,"Peer"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private blocker"))));
        assertThat(service.review(project,lead,friday).supportRequests().get(0).reason()).isEqualTo("Private blocker detail");
        service.resolve(id,lead,"Let's pair tomorrow.");
        var resolved=service.detail(t.id(),owner).supportRequests().get(0);
        assertThat(resolved.status()).isEqualTo(SupportRequestStatus.RESOLVED);assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE type='HELP_UPDATE' AND recipient_id=?",Integer.class,owner)).isEqualTo(1);
    }
    @Test void concurrentJobsAreIdempotentAndIgnoreFutureTasks() throws Exception {
        task(owner,0,true);task(owner,2,true);task(peer,7,true);
        var results=race(()->jobs.run(friday,false,project),()->jobs.run(friday,false,project));
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE type='CHECKPOINT_REMINDER' AND recipient_id=?",Integer.class,owner)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id=?",Integer.class,peer)).isZero();
        clock.set("2026-11-13T15:00:00Z");
        var finals=race(()->jobs.run(friday,true,project),()->jobs.run(friday,true,project));
        assertThat(finals.stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications WHERE type='CHECKPOINT_ALERT' AND recipient_id=?",Integer.class,lead)).isEqualTo(1);
    }
    @Test void noAlertWhenAllRequiredWorkReportedAchievedOrDone() {
        var a=task(owner,0,true);var b=task(owner,2,true);
        service.report(a.id(),owner,friday,TaskReportResult.ACHIEVED,"Ready",null);service.status(b.id(),owner,TaskStatus.DONE,null);
        assertThat(jobs.run(friday,false,project)).isZero();
        clock.set("2026-11-13T15:00:00Z");
        assertThat(jobs.run(friday,true,project)).isZero();
        clock.set("2026-11-13T15:00:00Z");
        var summary=service.dashboard(project,lead,friday).members().stream().filter(m->m.member().id().equals(owner)).findFirst().orElseThrow();
        assertThat(summary.state()).isEqualTo("GREEN");assertThat(summary.completed()).isEqualTo(1);assertThat(summary.reported()).isEqualTo(1);
    }
    @Test void tlStatusCorrectionNeedsRationaleAndNotificationReadsAreRecipientOnly() {
        var t=task(owner,0,true);
        statusError(()->service.status(t.id(),lead,TaskStatus.DONE,null),400);
        service.status(t.id(),lead,TaskStatus.DONE,"Confirmed completion with owner.");
        var id=db.queryForObject("SELECT id FROM notifications WHERE recipient_id=?",UUID.class,owner);
        statusError(()->service.markRead(id,peer),404);service.markRead(id,owner);
        assertThat(db.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE id=?",Boolean.class,id)).isTrue();
    }
    @Test void directClientRoleCannotReadUsersOrPrivateSupportEvenWithSelectGrants() throws Exception {
        db.execute("CREATE ROLE hanare_test_reader NOLOGIN");
        db.execute("GRANT USAGE ON SCHEMA public TO hanare_test_reader");
        db.execute("GRANT SELECT ON users, support_requests TO hanare_test_reader");
        var t=task(owner,0,false);service.needHelp(t.id(),owner,"Private reason",null);
        try(var connection=db.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement()) {
                statement.execute("SET LOCAL ROLE hanare_test_reader");
                try(var rows=statement.executeQuery("SELECT (SELECT count(*) FROM users), (SELECT count(*) FROM support_requests)")) {
                    rows.next();assertThat(rows.getInt(1)).isZero();assertThat(rows.getInt(2)).isZero();
                }
            } finally {connection.rollback();}
        }
        assertThat(db.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(4);
    }
    @Test void createRetryKeySurvivesConcurrentRequestsWithoutDuplicateOrOverwrite() throws Exception {
        var key=UUID.randomUUID();
        race(()->{service.create(project,lead,owner,"Retry safe","Scope","Output",friday,due(2),key);return 1;},
             ()->{service.create(project,lead,owner,"Retry safe","Scope","Output",friday,due(2),key);return 1;});
        assertThat(db.queryForObject("SELECT count(*) FROM tasks WHERE id=?",Integer.class,key)).isEqualTo(1);
        statusError(()->service.create(project,lead,owner,"Changed","Scope","Output",friday,due(2),key),409);
        assertThat(service.detail(key,owner).task().title()).isEqualTo("Retry safe");
    }
}
