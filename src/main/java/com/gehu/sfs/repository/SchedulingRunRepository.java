package com.gehu.sfs.repository;

import com.gehu.sfs.entity.SchedulingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SchedulingRunRepository extends JpaRepository<SchedulingRun, Integer> {
    List<SchedulingRun> findTop20ByOrderByIdDesc();

    @Query("select r.algorithm, count(r), avg(r.totalHeadMovement), min(r.totalHeadMovement), max(r.totalHeadMovement) from SchedulingRun r group by r.algorithm")
    List<Object[]> performance();
}
