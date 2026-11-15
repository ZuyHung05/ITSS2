package com.example.demo;

import com.example.demo.entity.*;
import static com.example.demo.dto.Views.*;
import com.example.demo.service.WeeklyPolicy;
import com.example.demo.exception.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WeeklyPolicyTest {
    private final LocalDate friday=LocalDate.of(2026,11,13);
    private final UUID user=UUID.randomUUID();
    private final Person member=new Person(user,"Member","member@test.invalid",MemberRole.MEMBER);
    private WeeklyPolicy policy(String instant) { return new WeeklyPolicy(Clock.fixed(Instant.parse(instant),ZoneOffset.UTC)); }
    private TaskView task(String deadline,TaskStatus status) {
        return new TaskView(UUID.randomUUID(),UUID.randomUUID(),user,"Member","Task","Scope","Output",friday.minusDays(4),
            OffsetDateTime.parse(deadline),status,PlanningStatus.CONFIRMED,UUID.randomUUID(),"Lead",OffsetDateTime.now());
    }
    private ReportView report(TaskView task,TaskReportResult result) { return new ReportView(UUID.randomUUID(),task.id(),friday,result,"Reason",null,OffsetDateTime.now()); }
    @Test void weeklyScopeUsesVietnamMondayAndSundayBoundaries() {
        var p=policy("2026-11-13T10:00:00Z");
        assertThat(p.due(task("2026-11-09T00:00:00+07:00",TaskStatus.TODO),friday)).isTrue();
        assertThat(p.due(task("2026-11-15T23:59:59.999+07:00",TaskStatus.TODO),friday)).isTrue();
        assertThat(p.due(task("2026-11-16T00:00:00+07:00",TaskStatus.TODO),friday)).isFalse();
        assertThat(p.due(task("2026-11-08T23:59:00+07:00",TaskStatus.TODO),friday)).isTrue();
        assertThat(p.due(task("2026-11-08T23:59:00+07:00",TaskStatus.DONE),friday)).isFalse();
    }
    @Test void cutoffIsExactlyFriday2200AndReportsOnlyOpenFriday() {
        assertThat(policy("2026-11-13T14:59:59Z").window(friday)).isEqualTo("OPEN");
        assertThat(policy("2026-11-13T15:00:00Z").window(friday)).isEqualTo("CLOSED");
        assertThat(policy("2026-11-12T16:59:59Z").window(friday)).isEqualTo("NOT_YET_DUE");
        assertThat(policy("2026-11-12T17:00:00Z").window(friday)).isEqualTo("OPEN");
        assertThatThrownBy(()->policy("2026-11-13T15:00:00Z").friday(friday.plusDays(1))).isInstanceOf(ApiException.class);
    }
    @Test void sundayNotAchievedIsNotAutomaticallyOverdue() {
        var p=policy("2026-11-13T15:01:00Z");var t=task("2026-11-15T23:59:00+07:00",TaskStatus.TODO);
        assertThat(p.overdue(t)).isFalse();
        assertThat(p.summarize(member,List.of(t),Map.of(t.id(),report(t,TaskReportResult.NOT_ACHIEVED)),friday,true).state()).isEqualTo("YELLOW");
    }
    @Test void finalColorsDistinguishCompletedReportedAndMissing() {
        var p=policy("2026-11-13T15:00:00Z");
        var a=task("2026-11-13T23:00:00+07:00",TaskStatus.TODO);var b=task("2026-11-13T23:00:00+07:00",TaskStatus.TODO);
        var done=task("2026-11-13T23:00:00+07:00",TaskStatus.DONE);
        assertThat(p.summarize(member,List.of(),Map.of(),friday,true).state()).isEqualTo("GRAY");
        assertThat(p.summarize(member,List.of(a,b),Map.of(),friday,true).state()).isEqualTo("RED");
        assertThat(p.summarize(member,List.of(done,b),Map.of(),friday,true).state()).isEqualTo("YELLOW");
        assertThat(p.summarize(member,List.of(done),Map.of(),friday,true).state()).isEqualTo("GREEN");
        var reports=Map.of(a.id(),report(a,TaskReportResult.ACHIEVED),b.id(),report(b,TaskReportResult.ACHIEVED));
        assertThat(p.summarize(member,List.of(a,b),reports,friday,true).state()).isEqualTo("GREEN");
        assertThat(p.summarize(member,List.of(a,b),Map.of(a.id(),report(a,TaskReportResult.ACHIEVED),b.id(),report(b,TaskReportResult.NOT_ACHIEVED)),friday,true).state()).isEqualTo("YELLOW");
    }
    @Test void beforeCutoffNeverMarksUnreportedMemberRedAndRedactsPeerNotes() {
        var p=policy("2026-11-13T14:59:59Z");var t=task("2026-11-13T23:00:00+07:00",TaskStatus.TODO);
        assertThat(p.summarize(member,List.of(t),Map.of(),friday,true).state()).isEqualTo("PENDING");
        var summary=p.summarize(member,List.of(t),Map.of(t.id(),report(t,TaskReportResult.NOT_ACHIEVED)),friday,false);
        assertThat(summary.tasks().get(0).report().note()).isNull();
    }
}
