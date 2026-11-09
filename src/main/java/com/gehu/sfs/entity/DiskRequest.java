package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Disk_Requests")
@Getter @Setter
public class DiskRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id") private Integer id;
    @Column(name = "block_no", nullable = false) private Integer blockNo;
    @Column(name = "requested_by", nullable = false) private Integer requestedBy;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
}
