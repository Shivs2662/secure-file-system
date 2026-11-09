package com.gehu.sfs.service;

import com.gehu.sfs.entity.DiskRequest;
import com.gehu.sfs.repository.DiskRequestRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/** Every file touch becomes pending block requests -- the input queue for the disk-scheduling simulator. */
@Service
public class DiskRequestService {
    private static final int MAX_PER_ACCESS = 12;
    private final DiskRequestRepository repo;
    public DiskRequestService(DiskRequestRepository repo) { this.repo = repo; }

    public void enqueue(Integer userId, List<Integer> blockNosInAccessOrder) {
        int n = 0;
        for (Integer b : blockNosInAccessOrder) {
            if (n++ >= MAX_PER_ACCESS) break;
            DiskRequest r = new DiskRequest();
            r.setBlockNo(b); r.setRequestedBy(userId); r.setRequestedAt(LocalDateTime.now());
            repo.save(r);
        }
    }
}
