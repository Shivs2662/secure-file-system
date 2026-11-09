package com.gehu.sfs.repository;

import com.gehu.sfs.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    List<Permission> findByFileId(Integer fileId);
    Optional<Permission> findByFileIdAndUserId(Integer fileId, Integer userId);
}
