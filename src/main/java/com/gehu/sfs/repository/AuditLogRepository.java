package com.gehu.sfs.repository;

import com.gehu.sfs.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
    Page<AuditLog> findByUserIdOrderByIdDesc(Integer userId, Pageable pageable);
}
