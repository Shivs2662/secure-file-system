package com.gehu.sfs.service.allocation;

/** One row of a file's block map, produced by an allocation algorithm. */
public record BlockPlan(int blockNo, Integer sequenceNo, Integer nextBlock, boolean indexBlock) {}
