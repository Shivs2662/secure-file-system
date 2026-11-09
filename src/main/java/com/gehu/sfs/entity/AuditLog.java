package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Audit_Logs")
@Getter @Setter
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id") private Long id;
    @Column(name = "user_id") private Integer userId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false) private String description;
    @Column(name = "event_time", nullable = false) private LocalDateTime eventTime;
}
