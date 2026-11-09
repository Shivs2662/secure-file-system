package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Access_Logs")
@Getter @Setter
public class AccessLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id") private Long id;
    @Column(name = "file_id", nullable = false) private Integer fileId;
    @Column(name = "user_id", nullable = false) private Integer userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccessAction action;
    @Column(name = "accessed_at", nullable = false) private LocalDateTime accessedAt;
}
