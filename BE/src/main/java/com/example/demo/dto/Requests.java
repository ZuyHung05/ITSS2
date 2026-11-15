package com.example.demo.dto;

import com.example.demo.entity.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Request bodies received by controllers. Current user identity comes from the session. */
public final class Requests {
    private Requests() {}
    public record CreateTask(UUID assigneeId,String title,String description,String expectedOutput,LocalDate startDate,OffsetDateTime deadline,UUID requestId) {}
    public record Plan(String description,String expectedOutput,LocalDate startDate,OffsetDateTime deadline) {}
    public record Status(TaskStatus status,String rationale) {}
    public record Report(TaskReportResult result,String note,String evidenceUrl) {}
    public record Help(String reason,String requestedHelp) {}
    public record Resolve(String leaderNote) {}
}
