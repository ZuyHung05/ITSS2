package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_requests")
@Getter @Setter @NoArgsConstructor
public class SupportRequest {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(nullable = false, columnDefinition = "text")
    private String reason;
    @Column(name = "requested_help", columnDefinition = "text")
    private String requestedHelp;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestStatus status = SupportRequestStatus.OPEN;
    @Column(name = "leader_note", columnDefinition = "text")
    private String leaderNote;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
