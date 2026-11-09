package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Files")
@Getter @Setter
public class FileEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id") private Integer id;
    @Column(name = "file_name", nullable = false) private String name;
    @Column(name = "folder_id", nullable = false) private Integer folderId;
    @Column(name = "owner_id", nullable = false) private Integer ownerId;
    @Column(name = "file_size_bytes", nullable = false) private Long sizeBytes;
    @Column(name = "file_type", nullable = false) private String fileType;
    @Enumerated(EnumType.STRING) @Column(name = "allocation_type", nullable = false) private AllocationType allocationType;
    @Column(name = "current_version", nullable = false) private Integer currentVersion = 1;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private LocalDateTime updatedAt;
}
