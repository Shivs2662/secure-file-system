package com.gehu.sfs.service.scheduling;

import com.gehu.sfs.entity.Enums.Algorithm;
import java.util.List;

/** OS module: decides the ORDER in which pending block requests are serviced. */
public interface SchedulingStrategy {
    Algorithm algorithm();
    ScheduleResult schedule(List<Integer> requests, int headStart, int diskBlocks);
}
