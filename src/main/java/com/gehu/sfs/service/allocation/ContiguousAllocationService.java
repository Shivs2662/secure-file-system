package com.gehu.sfs.service.allocation;

import com.gehu.sfs.entity.Enums.AllocationType;
import com.gehu.sfs.exception.AllocationFailedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** First-fit: find the first run of k consecutive free blocks. */
@Service
public class ContiguousAllocationService implements AllocationStrategy {
    public AllocationType type() { return AllocationType.CONTIGUOUS; }

    public List<BlockPlan> allocate(int k, Set<Integer> used, int diskBlocks) {
        int runStart = -1, runLen = 0;
        for (int b = 0; b < diskBlocks; b++) {
            if (used.contains(b)) { runStart = -1; runLen = 0; continue; }
            if (runStart < 0) runStart = b;
            if (++runLen == k) {
                List<BlockPlan> plan = new ArrayList<>();
                for (int i = 0; i < k; i++) plan.add(new BlockPlan(runStart + i, i, null, false));
                return plan;
            }
        }
        throw new AllocationFailedException("No run of " + k + " contiguous free blocks (external fragmentation or disk full)");
    }
}
