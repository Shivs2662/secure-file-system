package com.gehu.sfs.service.scheduling;

import com.gehu.sfs.entity.Enums.Algorithm;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class SstfSchedulingService implements SchedulingStrategy {
    public Algorithm algorithm() { return Algorithm.SSTF; }
    public ScheduleResult schedule(List<Integer> requests, int head, int diskBlocks) {
        List<Integer> pending = new ArrayList<>(requests), order = new ArrayList<>();
        int pos = head;
        while (!pending.isEmpty()) {
            int best = 0;
            for (int i = 1; i < pending.size(); i++)
                if (Math.abs(pending.get(i) - pos) < Math.abs(pending.get(best) - pos)) best = i;
            pos = pending.remove(best);
            order.add(pos);
        }
        return ScheduleResult.of(order, head);
    }
}
