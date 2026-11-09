package com.gehu.sfs.repository;

import com.gehu.sfs.entity.DiskRequest;
import com.gehu.sfs.entity.Enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiskRequestRepository extends JpaRepository<DiskRequest, Integer> {
    List<DiskRequest> findTop100ByStatusOrderByIdAsc(RequestStatus status);
}
