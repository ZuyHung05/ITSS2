package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class ProjectMemberId implements Serializable {
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
