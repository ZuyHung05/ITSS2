package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contributions")
@Getter @Setter @NoArgsConstructor
public class Contribution {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "help_request_id", nullable = false, unique = true)
    private UUID helpRequestId;
    @Column(name = "result_note", columnDefinition = "text")
    private String resultNote;
    @Column(name = "evidence_url", columnDefinition = "text")
    private String evidenceUrl;
    @Column(name = "actual_minutes")
    private Integer actualMinutes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status = ContributionStatus.PENDING;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;
}
