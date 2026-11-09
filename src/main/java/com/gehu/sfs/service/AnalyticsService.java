package com.gehu.sfs.service;

import com.gehu.sfs.entity.AuditLog;
import com.gehu.sfs.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AnalyticsService {
    private final FileRepository files; private final SchedulingRunRepository runs; private final AccessLogRepository access;
    private final SimulationService sim; private final UserRepository users; private final AuditLogRepository audit;
    public AnalyticsService(FileRepository files, SchedulingRunRepository runs, AccessLogRepository access, SimulationService sim,
                            UserRepository users, AuditLogRepository audit) {
        this.files = files; this.runs = runs; this.access = access; this.sim = sim; this.users = users; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> storage(Integer userId) {
        Map<String, Object> pool = sim.pool(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : List.of("totalBlocks", "blockSize", "usedBlocks", "freeBlocks", "largestFreeRun")) out.put(k, pool.get(k));
        int total = (Integer) pool.get("totalBlocks"), free = (Integer) pool.get("freeBlocks");
        // external fragmentation = how much of the free space is NOT in the largest free run
        out.put("fragmentationPct", free == 0 ? 0 : Math.round(100.0 * (free - (Integer) pool.get("largestFreeRun")) / free));
        out.put("myBytes", files.sumBytesByOwner(userId));
        out.put("myFiles", files.findByOwnerId(userId).size());
        List<Map<String, Object>> by = new ArrayList<>();
        for (Object[] r : files.statsByAllocation()) by.add(Map.of("allocationType", r[0], "files", r[1], "bytes", r[2]));
        out.put("byAllocation", by);
        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] r : access.mostUsedFiles(userId, PageRequest.of(0, 5))) {
            files.findById((Integer) r[0]).ifPresent(f -> top.add(Map.of("fileId", f.getId(), "name", f.getName(), "accesses", r[1])));
        }
        out.put("mostUsed", top);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> schedulingPerformance() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : runs.performance())
            out.add(Map.of("algorithm", r[0], "runs", r[1], "avgMovement", Math.round(((Number) r[2]).doubleValue() * 10) / 10.0, "best", r[3], "worst", r[4]));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> storageByUser() {
        List<Map<String, Object>> out = new ArrayList<>();
        users.findAll().forEach(u -> out.add(Map.of("userId", u.getId(), "username", u.getUsername(),
                "files", files.findByOwnerId(u.getId()).size(), "bytes", files.sumBytesByOwner(u.getId()))));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> auditLogs(Integer userId, int page, int size) {
        var p = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100));
        var res = userId == null ? audit.findAllByOrderByIdDesc(p) : audit.findByUserIdOrderByIdDesc(userId, p);
        Map<Integer, String> names = new HashMap<>();
        users.findAll().forEach(u -> names.put(u.getId(), u.getUsername()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditLog a : res.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("user", a.getUserId() == null ? "system" : names.getOrDefault(a.getUserId(), "?"));
            m.put("event", a.getEventType()); m.put("description", a.getDescription()); m.put("time", a.getEventTime());
            rows.add(m);
        }
        return Map.of("rows", rows, "page", res.getNumber(), "totalPages", res.getTotalPages(), "total", res.getTotalElements());
    }
}
