package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "Scheduling_Results")
@Getter @Setter
public class SchedulingResultRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id") private Integer id;
    @Column(name = "run_id", nullable = false) private Integer runId;
    @Column(name = "step_no", nullable = false) private Integer stepNo;
    @Column(name = "block_no", nullable = false) private Integer blockNo;
    @Column(name = "distance_from_prev", nullable = false) private Integer distanceFromPrev;
}
