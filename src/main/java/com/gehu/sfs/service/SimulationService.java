package com.gehu.sfs.service;

import com.gehu.sfs.config.SfsProperties;
import com.gehu.sfs.dto.Requests.AllocSim;
import com.gehu.sfs.dto.Requests.ScheduleReq;
import com.gehu.sfs.entity.*;
import com.gehu.sfs.entity.Enums.Algorithm;
import com.gehu.sfs.entity.Enums.RequestStatus;
import com.gehu.sfs.exception.AllocationFailedException;
import com.gehu.sfs.exception.ApiException;
import com.gehu.sfs.repository.*;
import com.gehu.sfs.service.allocation.AllocationStrategy;
import com.gehu.sfs.service.allocation.BlockPlan;
import com.gehu.sfs.service.scheduling.ScheduleResult;
import com.gehu.sfs.service.scheduling.SchedulingStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Simulation endpoints: disk pool view, allocation dry-run, disk scheduling runs + comparison. */
@Service
public class SimulationService {
    private final FileBlockRepository blocks; private final FileRepository files; private final PermissionService pm;
    private final DiskRequestRepository requests; private final SchedulingRunRepository runs; private final SchedulingResultRepository results;
    private final SfsProperties props; private final List<AllocationStrategy> allocs; private final List<SchedulingStrategy> schedulers;

    public SimulationService(FileBlockRepository blocks, FileRepository files, PermissionService pm, DiskRequestRepository requests,
                             SchedulingRunRepository runs, SchedulingResultRepository results, SfsProperties props,
                             List<AllocationStrategy> allocs, List<SchedulingStrategy> schedulers) {
        this.blocks = blocks; this.files = files; this.pm = pm; this.requests = requests; this.runs = runs; this.results = results;
        this.props = props; this.allocs = allocs; this.schedulers = schedulers;
    }

    // ------------------------------------------------------------ pool / allocation
    private int largestFreeRun(Set<Integer> used) {
        int best = 0, cur = 0;
        for (int b = 0; b < props.diskBlocks; b++) { if (used.contains(b)) cur = 0; else best = Math.max(best, ++cur); }
        return best;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pool(Integer userId) {
        List<FileBlock> all = blocks.findAll();
        Set<Integer> fileIds = new HashSet<>(); all.forEach(b -> fileIds.add(b.getFileId()));
        Map<Integer, FileEntity> fileMap = new HashMap<>();
        files.findAllById(fileIds).forEach(f -> fileMap.put(f.getId(), f));
        Map<Integer, Boolean> canSee = new HashMap<>();
        List<Map<String, Object>> cells = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (FileBlock b : all) {
            used.add(b.getBlockNo());
            FileEntity f = fileMap.get(b.getFileId());
            boolean visible = f != null && canSee.computeIfAbsent(f.getId(), k -> pm.of(f, userId).read());
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("blockNo", b.getBlockNo()); c.put("fileId", b.getFileId());
            c.put("fileName", visible ? f.getName() : "(another user's file)");
            c.put("allocationType", f == null ? null : f.getAllocationType());
            c.put("index", b.isIndexBlock()); c.put("sequenceNo", b.getSequenceNo()); c.put("nextBlock", b.getNextBlock());
            c.put("mine", f != null && f.getOwnerId().equals(userId));
            cells.add(c);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalBlocks", props.diskBlocks); out.put("blockSize", props.blockSize);
        out.put("usedBlocks", used.size()); out.put("freeBlocks", props.diskBlocks - used.size());
        out.put("largestFreeRun", largestFreeRun(used)); out.put("cells", cells);
        return out;
    }

    /** Dry run: shows where a file of N blocks WOULD go under the chosen strategy. Nothing is saved. */
    @Transactional(readOnly = true)
    public Map<String, Object> simulateAllocation(AllocSim req) {
        Set<Integer> used = new HashSet<>(blocks.findAllUsedBlockNos());
        AllocationStrategy st = allocs.stream().filter(a -> a.type() == req.type()).findFirst().orElseThrow(() -> ApiException.bad("Unknown type"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", req.type()); out.put("requestedBlocks", req.blocks());
        try {
            List<BlockPlan> plan = st.allocate(req.blocks(), used, props.diskBlocks);
            out.put("success", true); out.put("plan", plan);
        } catch (AllocationFailedException e) {
            out.put("success", false); out.put("message", e.getMessage());
        }
        out.put("largestFreeRun", largestFreeRun(used)); out.put("freeBlocks", props.diskBlocks - used.size());
        return out;
    }

    // ------------------------------------------------------------ scheduling
    @Transactional(readOnly = true)
    public List<Integer> pendingQueue() {
        List<Integer> q = new ArrayList<>();
        requests.findTop100ByStatusOrderByIdAsc(RequestStatus.PENDING).forEach(r -> q.add(r.getBlockNo()));
        return q;
    }

    private List<Integer> resolveRequests(ScheduleReq req, List<DiskRequest> queueRows) {
        List<Integer> list;
        if (req.useQueue()) {
            queueRows.addAll(requests.findTop100ByStatusOrderByIdAsc(RequestStatus.PENDING));
            list = new ArrayList<>(); queueRows.forEach(r -> list.add(r.getBlockNo()));
        } else list = req.requests() == null ? new ArrayList<>() : new ArrayList<>(req.requests());
        if (list.isEmpty()) throw ApiException.bad("No requests to schedule (enter some block numbers, or generate some by opening files)");
        if (list.size() > 100) throw ApiException.bad("At most 100 requests per run");
        for (Integer b : list) if (b == null || b < 0 || b >= props.diskBlocks) throw ApiException.bad("Block numbers must be between 0 and " + (props.diskBlocks - 1));
        if (req.headStart() >= props.diskBlocks) throw ApiException.bad("Head start must be between 0 and " + (props.diskBlocks - 1));
        return list;
    }

    private Map<String, Object> runOne(SchedulingStrategy s, List<Integer> reqs, int head) {
        ScheduleResult r = s.schedule(reqs, head, props.diskBlocks);
        SchedulingRun run = new SchedulingRun();
        run.setAlgorithm(s.algorithm()); run.setHeadStart(head); run.setRunAt(LocalDateTime.now()); run.setTotalHeadMovement(r.totalMovement());
        run = runs.save(run);
        List<SchedulingResultRow> rows = new ArrayList<>();
        for (int i = 0; i < r.order().size(); i++) {
            SchedulingResultRow row = new SchedulingResultRow();
            row.setRunId(run.getId()); row.setStepNo(i + 1); row.setBlockNo(r.order().get(i)); row.setDistanceFromPrev(r.distances().get(i));
            rows.add(row);
        }
        results.saveAll(rows);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", run.getId()); out.put("algorithm", s.algorithm()); out.put("headStart", head);
        out.put("order", r.order()); out.put("distances", r.distances()); out.put("totalMovement", r.totalMovement());
        out.put("avgSeek", Math.round(r.totalMovement() * 10.0 / reqs.size()) / 10.0);
        return out;
    }

    private void consume(List<DiskRequest> rows, boolean consume) {
        if (!consume) return;
        rows.forEach(r -> r.setStatus(RequestStatus.SERVICED));
        requests.saveAll(rows);
    }

    @Transactional
    public Map<String, Object> schedule(ScheduleReq req) {
        List<DiskRequest> rows = new ArrayList<>();
        List<Integer> reqs = resolveRequests(req, rows);
        SchedulingStrategy s = schedulers.stream().filter(x -> x.algorithm() == req.algorithm()).findFirst().orElseThrow();
        Map<String, Object> out = runOne(s, reqs, req.headStart());
        out.put("requests", reqs);
        consume(rows, req.useQueue() && req.consume());
        return out;
    }

    /** Same request set through all four algorithms -- the basis of the comparison dashboard. */
    @Transactional
    public Map<String, Object> compare(ScheduleReq req) {
        List<DiskRequest> rows = new ArrayList<>();
        List<Integer> reqs = resolveRequests(req, rows);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Algorithm a : Algorithm.values())
            schedulers.stream().filter(x -> x.algorithm() == a).findFirst().ifPresent(s -> res.add(runOne(s, reqs, req.headStart())));
        consume(rows, req.useQueue() && req.consume());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("headStart", req.headStart()); out.put("requests", reqs); out.put("diskBlocks", props.diskBlocks); out.put("results", res);
        return out;
    }

    @Transactional(readOnly = true)
    public List<SchedulingRun> history() { return runs.findTop20ByOrderByIdDesc(); }
}
