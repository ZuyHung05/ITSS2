package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_requests")
@Getter @Setter @NoArgsConstructor
public class HelpRequest {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    private OffsetDateTime deadline;
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;
    @Column(name = "volunteer_id")
    private UUID volunteerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HelpRequestStatus status = HelpRequestStatus.OPEN;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
