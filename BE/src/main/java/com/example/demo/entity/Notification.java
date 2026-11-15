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
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @Column(name = "checkpoint_date")
    private LocalDate checkpointDate;
    @Column(name = "read_at")
    private OffsetDateTime readAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
