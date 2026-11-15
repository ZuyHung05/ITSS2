package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor
public class Project {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(nullable = false, length = 150)
    private String name;
    private OffsetDateTime deadline;
}
