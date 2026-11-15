package com.example.demo.dto;

import com.example.demo.entity.*;
import java.time.*;
import java.util.*;

public final class Views {
    private Views() {}
    public record Person(UUID id, String name, String email, MemberRole role) {}
    public record ProjectView(UUID id, String name, OffsetDateTime deadline, MemberRole role) {}
    public record TaskView(UUID id, UUID projectId, UUID assigneeId, String assigneeName,
                           String title, String description, String expectedOutput, LocalDate startDate,
                           OffsetDateTime deadline, TaskStatus status, PlanningStatus planningStatus,
                           UUID confirmedBy, String confirmedByName, OffsetDateTime confirmedAt) {}
    public record ReportView(UUID id, UUID taskId, LocalDate checkpointDate, TaskReportResult result,
                             String note, String evidenceUrl, OffsetDateTime reportedAt) {}
    public record SupportView(UUID id, UUID taskId, String taskTitle, UUID requestedBy, String requesterName,
                              String reason, String requestedHelp, SupportRequestStatus status,
                              String leaderNote, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {}
    public record NotificationView(UUID id, UUID projectId, NotificationType type, String title, String message,
                                   LocalDate checkpointDate, OffsetDateTime readAt, OffsetDateTime createdAt) {}
    public record WeeklyTask(TaskView task, ReportView report, boolean overdue, boolean reportRequired) {}
    public record MemberSummary(Person member, int due, int completed, int reported, int achieved,
                                int notAchieved, int missing, String state, String explanation,
                                List<WeeklyTask> tasks) {}
    public record Dashboard(ProjectView project, LocalDate checkpointDate, OffsetDateTime cutoff,
                            boolean closed, boolean historicalApproximation, List<MemberSummary> members,
                            List<TaskView> myTasks, int openSupportRequests, int issues) {}
    public record TaskDetail(TaskView task, MemberRole role, boolean owner,
                             List<ReportView> reports, List<SupportView> supportRequests) {}
    public record CheckpointView(ProjectView project, LocalDate checkpointDate, OffsetDateTime cutoff,
                                 String window, List<WeeklyTask> tasks, List<WeeklyTask> completed,
                                 Map<UUID,ReportView> previousReports) {}
    public record ReviewView(Dashboard dashboard, List<TaskView> drafts, List<SupportView> supportRequests) {}
}
