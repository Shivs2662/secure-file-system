package com.gehu.sfs;

import com.gehu.sfs.exception.AllocationFailedException;
import com.gehu.sfs.service.allocation.*;
import com.gehu.sfs.service.scheduling.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests: no Spring context, no database. Expected numbers are the worked examples from the report. */
class AlgorithmTests {
    private static final List<Integer> REQ = List.of(82, 170, 43, 140, 24, 16, 190);

    @Test void fcfs() { assertEquals(642, new FcfsSchedulingService().schedule(REQ, 50, 200).totalMovement()); }
    @Test void sstf() {
        var r = new SstfSchedulingService().schedule(REQ, 50, 200);
        assertEquals(List.of(43, 24, 16, 82, 140, 170, 190), r.order());
        assertEquals(208, r.totalMovement());
    }
    @Test void scan() {
        var r = new ScanSchedulingService().schedule(REQ, 50, 200);
        assertEquals(List.of(82, 140, 170, 190, 199, 43, 24, 16), r.order());
        assertEquals(332, r.totalMovement());
    }
    @Test void cscan() {
        var r = new CScanSchedulingService().schedule(REQ, 50, 200);
        assertEquals(List.of(82, 140, 170, 190, 199, 0, 16, 24, 43), r.order());
        assertEquals(391, r.totalMovement());
    }

    @Test void contiguousFirstFit() {
        Set<Integer> used = new HashSet<>(List.of(0, 1, 2, 10, 11));
        var plan = new ContiguousAllocationService().allocate(5, used, 200);
        assertEquals(3, plan.get(0).blockNo());
        assertEquals(7, plan.get(4).blockNo());
    }
    @Test void contiguousFailsOnFragmentation() {
        Set<Integer> used = new HashSet<>(List.of(1, 3, 5));          // free runs of length 1 only
        assertThrows(AllocationFailedException.class, () -> new ContiguousAllocationService().allocate(2, used, 7));
    }
    @Test void linkedChainEndsWithNull() {
        var plan = new LinkedAllocationService().allocate(4, new HashSet<>(), 200);
        assertEquals(4, plan.size());
        for (int i = 0; i < 3; i++) assertEquals(plan.get(i + 1).blockNo(), plan.get(i).nextBlock());
        assertNull(plan.get(3).nextBlock());
    }
    @Test void indexedHasOneIndexBlockPlusData() {
        var plan = new IndexedAllocationService().allocate(4, new HashSet<>(), 200);
        assertEquals(5, plan.size());
        assertEquals(1, plan.stream().filter(BlockPlan::indexBlock).count());
        assertEquals(5, plan.stream().map(BlockPlan::blockNo).distinct().count());
    }
    @Test void allocationNeverReusesUsedBlocks() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 195; i++) used.add(i);
        assertThrows(AllocationFailedException.class, () -> new LinkedAllocationService().allocate(6, used, 200));
        assertThrows(AllocationFailedException.class, () -> new IndexedAllocationService().allocate(5, used, 200));
    }
}
