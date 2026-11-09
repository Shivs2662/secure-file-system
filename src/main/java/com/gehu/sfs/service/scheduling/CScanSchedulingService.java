package com.gehu.sfs.service.scheduling;

import com.gehu.sfs.entity.Enums.Algorithm;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Circular SCAN: sweep upward to the end, jump to block 0 (jump is counted as movement), keep sweeping upward. */
@Service
public class CScanSchedulingService implements SchedulingStrategy {
    public Algorithm algorithm() { return Algorithm.CSCAN; }
    public ScheduleResult schedule(List<Integer> requests, int head, int diskBlocks) {
        List<Integer> upper = new ArrayList<>(), lower = new ArrayList<>();
        for (int r : requests) (r >= head ? upper : lower).add(r);
        upper.sort(Comparator.naturalOrder());
        lower.sort(Comparator.naturalOrder());
        List<Integer> order = new ArrayList<>(upper);
        if (!lower.isEmpty()) { order.add(diskBlocks - 1); order.add(0); order.addAll(lower); }
        else if (!upper.isEmpty()) order.add(diskBlocks - 1);
        return ScheduleResult.of(order, head);
    }
}
