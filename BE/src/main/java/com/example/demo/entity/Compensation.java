package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "compensations")
@Getter @Setter @NoArgsConstructor
public class Compensation {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "contribution_id", nullable = false, unique = true)
    private UUID contributionId;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationStatus status = CompensationStatus.PROPOSED;
    @Column(name = "agreed_at")
    private OffsetDateTime agreedAt;
    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;
}
