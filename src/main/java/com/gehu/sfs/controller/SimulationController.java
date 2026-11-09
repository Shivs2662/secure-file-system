package com.gehu.sfs.controller;

import com.gehu.sfs.dto.Requests.AllocSim;
import com.gehu.sfs.dto.Requests.ScheduleReq;
import com.gehu.sfs.entity.Enums.Algorithm;
import com.gehu.sfs.entity.SchedulingRun;
import com.gehu.sfs.security.CurrentUser;
import com.gehu.sfs.service.AnalyticsService;
import com.gehu.sfs.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {
    private final SimulationService sim; private final AnalyticsService analytics;
    public SimulationController(SimulationService s, AnalyticsService a) { sim = s; analytics = a; }

    @GetMapping("/simulate/pool") public Map<String, Object> pool() { return sim.pool(CurrentUser.id()); }
    @PostMapping("/simulate/allocation") public Map<String, Object> allocation(@Valid @RequestBody AllocSim req) { return sim.simulateAllocation(req); }
    @GetMapping("/simulate/queue") public List<Integer> queue() { return sim.pendingQueue(); }
    @PostMapping("/simulate/scheduling") public Map<String, Object> schedule(@Valid @RequestBody ScheduleReq req) { return sim.schedule(req); }
    @GetMapping("/simulate/history") public List<SchedulingRun> history() { return sim.history(); }

    /** GET /api/simulate/scheduling/compare?headStart=50&requests=82,170,43  (omit requests to use the pending disk queue) */
    @GetMapping("/simulate/scheduling/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "50") int headStart, @RequestParam(required = false) String requests,
                                       @RequestParam(defaultValue = "false") boolean consume) {
        List<Integer> list = new ArrayList<>();
        boolean useQueue = requests == null || requests.isBlank();
        if (!useQueue) for (String s : requests.split(",")) {
            try { list.add(Integer.parseInt(s.trim())); } catch (NumberFormatException e) { throw com.gehu.sfs.exception.ApiException.bad("Requests must be comma-separated integers"); }
        }
        return sim.compare(new ScheduleReq(Algorithm.FCFS, headStart, list, useQueue, consume));
    }

    @GetMapping("/analytics/storage") public Map<String, Object> storage() { return analytics.storage(CurrentUser.id()); }
    @GetMapping("/analytics/scheduling") public List<Map<String, Object>> performance() { return analytics.schedulingPerformance(); }
}
