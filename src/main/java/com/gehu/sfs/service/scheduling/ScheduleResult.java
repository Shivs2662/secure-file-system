package com.gehu.sfs.service.scheduling;

import java.util.ArrayList;
import java.util.List;

/** order = every cylinder the head visits (including disk-end/jump points), distances = per-step movement. */
public record ScheduleResult(List<Integer> order, List<Integer> distances, int totalMovement) {
    public static ScheduleResult of(List<Integer> order, int headStart) {
        List<Integer> d = new ArrayList<>();
        int pos = headStart, total = 0;
        for (int b : order) { int dist = Math.abs(b - pos); d.add(dist); total += dist; pos = b; }
        return new ScheduleResult(order, d, total);
    }
}
