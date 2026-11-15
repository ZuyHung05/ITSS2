package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor
public class Task {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "assignee_id", nullable = false)
    private UUID assigneeId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "expected_output", columnDefinition = "text")
    private String expectedOutput;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(nullable = false)
    private OffsetDateTime deadline;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;
    @Enumerated(EnumType.STRING)
    @Column(name = "planning_status", nullable = false, length = 20)
    private PlanningStatus planningStatus = PlanningStatus.DRAFT;
    @Column(name = "confirmed_by")
    private UUID confirmedBy;
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
