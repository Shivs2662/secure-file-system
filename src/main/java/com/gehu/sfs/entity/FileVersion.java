package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "File_Versions")
@Getter @Setter
public class FileVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id") private Integer id;
    @Column(name = "file_id", nullable = false) private Integer fileId;
    @Column(name = "version_no", nullable = false) private Integer versionNo;
    @Column(name = "modified_by", nullable = false) private Integer modifiedBy;
    @Column(name = "modified_at", nullable = false) private LocalDateTime modifiedAt;
    @Column(name = "file_size_bytes", nullable = false) private Long sizeBytes;
}
