package com.gehu.sfs.service.allocation;

import com.gehu.sfs.entity.Enums.AllocationType;
import java.util.List;
import java.util.Set;

/** OS module: decides WHICH blocks a file gets. Pure logic, no database access. */
public interface AllocationStrategy {
    AllocationType type();
    /** @param dataBlocks number of data blocks needed; @param used block numbers already taken; @param diskBlocks pool size */
    List<BlockPlan> allocate(int dataBlocks, Set<Integer> used, int diskBlocks);
}
