package com.gehu.sfs.service.scheduling;

import com.gehu.sfs.entity.Enums.Algorithm;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class FcfsSchedulingService implements SchedulingStrategy {
    public Algorithm algorithm() { return Algorithm.FCFS; }
    public ScheduleResult schedule(List<Integer> requests, int head, int diskBlocks) {
        return ScheduleResult.of(new ArrayList<>(requests), head);
    }
}
