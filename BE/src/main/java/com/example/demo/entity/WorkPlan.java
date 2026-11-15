package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

/** Legacy v1 model; not mapped by the v2 application. */
@Deprecated
@Getter @Setter @NoArgsConstructor
public class WorkPlan {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "task_id", nullable = false, unique = true)
    private UUID taskId;
    @Column(name = "required_input", nullable = false, columnDefinition = "text")
    private String requiredInput;
    @Column(name = "plan_a", nullable = false, columnDefinition = "text")
    private String planA;
    @Column(name = "plan_b", nullable = false, columnDefinition = "text")
    private String planB;
}
