package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "File_Locks")
@Getter @Setter
public class FileLock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lock_id") private Integer id;
    @Column(name = "file_id", nullable = false) private Integer fileId;
    @Column(name = "user_id", nullable = false) private Integer userId;
    @Enumerated(EnumType.STRING) @Column(name = "lock_type", nullable = false) private LockType lockType = LockType.WRITE;
    @Column(name = "acquired_at", nullable = false) private LocalDateTime acquiredAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
}
