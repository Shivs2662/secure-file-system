package com.gehu.sfs.repository;

import com.gehu.sfs.entity.SchedulingResultRow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchedulingResultRepository extends JpaRepository<SchedulingResultRow, Integer> {
    List<SchedulingResultRow> findByRunIdOrderByStepNo(Integer runId);
}
