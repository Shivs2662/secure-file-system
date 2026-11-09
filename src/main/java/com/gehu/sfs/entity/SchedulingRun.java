package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Scheduling_Runs")
@Getter @Setter
public class SchedulingRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id") private Integer id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Algorithm algorithm;
    @Column(name = "head_start", nullable = false) private Integer headStart;
    @Column(name = "run_at", nullable = false) private LocalDateTime runAt;
    @Column(name = "total_head_movement", nullable = false) private Integer totalHeadMovement;
}
