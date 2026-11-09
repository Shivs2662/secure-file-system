package com.gehu.sfs.service.scheduling;

import com.gehu.sfs.entity.Enums.Algorithm;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Elevator: sweep upward to the last block, reverse, sweep downward. */
@Service
public class ScanSchedulingService implements SchedulingStrategy {
    public Algorithm algorithm() { return Algorithm.SCAN; }
    public ScheduleResult schedule(List<Integer> requests, int head, int diskBlocks) {
        List<Integer> upper = new ArrayList<>(), lower = new ArrayList<>();
        for (int r : requests) (r >= head ? upper : lower).add(r);
        upper.sort(Comparator.naturalOrder());
        lower.sort(Comparator.reverseOrder());
        List<Integer> order = new ArrayList<>(upper);
        if (!lower.isEmpty() || !upper.isEmpty()) order.add(diskBlocks - 1);   // go to the end of the disk
        order.addAll(lower);
        return ScheduleResult.of(order, head);
    }
}
