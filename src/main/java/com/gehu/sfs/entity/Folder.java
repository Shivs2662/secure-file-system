package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Folders")
@Getter @Setter
public class Folder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id") private Integer id;
    @Column(name = "folder_name", nullable = false) private String name;
    @Column(name = "owner_id", nullable = false) private Integer ownerId;
    @Column(name = "parent_folder_id") private Integer parentId;
    @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
}
