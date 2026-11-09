package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Permissions")
@Getter @Setter
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id") private Integer id;
    @Column(name = "file_id", nullable = false) private Integer fileId;
    @Column(name = "user_id", nullable = false) private Integer userId;
    @Column(name = "can_read", nullable = false) private boolean canRead = true;
    @Column(name = "can_write", nullable = false) private boolean canWrite = false;
    @Column(name = "granted_at", insertable = false, updatable = false) private LocalDateTime grantedAt;
}
