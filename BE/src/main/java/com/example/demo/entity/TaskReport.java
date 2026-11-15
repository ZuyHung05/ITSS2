package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_reports", uniqueConstraints =
        @UniqueConstraint(name = "uq_task_report_checkpoint", columnNames = {"task_id", "checkpoint_date"}))
@Getter @Setter @NoArgsConstructor
public class TaskReport {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "checkpoint_date", nullable = false)
    private LocalDate checkpointDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskReportResult result;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "evidence_url", columnDefinition = "text")
    private String evidenceUrl;
    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt = OffsetDateTime.now();
}
