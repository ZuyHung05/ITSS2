package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Legacy v1 model; not mapped by the v2 application. */
@Deprecated
@Getter @Setter @NoArgsConstructor
public class Checkpoint {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "work_plan_id", nullable = false)
    private UUID workPlanId;
    @Column(name = "check_at", nullable = false)
    private OffsetDateTime checkAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "input_status", nullable = false, length = 20)
    private CheckpointStatus inputStatus = CheckpointStatus.PENDING;
    @Column(name = "checked_by")
    private UUID checkedBy;
    @Column(name = "checked_at")
    private OffsetDateTime checkedAt;
}
