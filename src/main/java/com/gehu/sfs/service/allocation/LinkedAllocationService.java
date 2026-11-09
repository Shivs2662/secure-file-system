package com.gehu.sfs.service.allocation;

import com.gehu.sfs.entity.Enums.AllocationType;
import com.gehu.sfs.exception.AllocationFailedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Any k free blocks (scattered on purpose so the chain is visible); each block points to the next. */
@Service
public class LinkedAllocationService implements AllocationStrategy {
    private final Random random = new Random();
    public AllocationType type() { return AllocationType.LINKED; }

    public List<BlockPlan> allocate(int k, Set<Integer> used, int diskBlocks) {
        List<Integer> free = new ArrayList<>();
        for (int b = 0; b < diskBlocks; b++) if (!used.contains(b)) free.add(b);
        if (free.size() < k) throw new AllocationFailedException("Need " + k + " blocks but only " + free.size() + " are free");
        Collections.shuffle(free, random);
        List<Integer> chain = free.subList(0, k);
        List<BlockPlan> plan = new ArrayList<>();
        for (int i = 0; i < k; i++)
            plan.add(new BlockPlan(chain.get(i), i, i == k - 1 ? null : chain.get(i + 1), false));
        return plan;
    }
}
