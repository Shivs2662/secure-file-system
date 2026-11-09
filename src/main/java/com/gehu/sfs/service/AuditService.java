package com.gehu.sfs.service;

import com.gehu.sfs.entity.AuditLog;
import com.gehu.sfs.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(Integer userId, String type, String description) {
        AuditLog a = new AuditLog();
        a.setUserId(userId);
        a.setEventType(type);
        a.setDescription(description.length() > 250 ? description.substring(0, 250) : description);
        a.setEventTime(LocalDateTime.now());
        repo.save(a);
    }
}
