package com.gehu.sfs.service.allocation;

import com.gehu.sfs.entity.Enums.AllocationType;
import com.gehu.sfs.exception.AllocationFailedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** One index block + k data blocks. The index block row lists nothing itself; data rows reference it by sequence order. */
@Service
public class IndexedAllocationService implements AllocationStrategy {
    private final Random random = new Random();
    public AllocationType type() { return AllocationType.INDEXED; }

    public List<BlockPlan> allocate(int k, Set<Integer> used, int diskBlocks) {
        List<Integer> free = new ArrayList<>();
        for (int b = 0; b < diskBlocks; b++) if (!used.contains(b)) free.add(b);
        if (free.size() < k + 1) throw new AllocationFailedException("Need " + (k + 1) + " blocks (incl. index block) but only " + free.size() + " are free");
        Collections.shuffle(free, random);
        List<BlockPlan> plan = new ArrayList<>();
        plan.add(new BlockPlan(free.get(0), null, null, true));
        for (int i = 0; i < k; i++) plan.add(new BlockPlan(free.get(i + 1), i, null, false));
        return plan;
    }
}
