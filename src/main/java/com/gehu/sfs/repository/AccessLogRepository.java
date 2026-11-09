package com.gehu.sfs.repository;

import com.gehu.sfs.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
    List<AccessLog> findTop50ByFileIdOrderByAccessedAtDescIdDesc(Integer fileId);

    @Query("select a.fileId, count(a) from AccessLog a where a.fileId in (select f.id from FileEntity f where f.ownerId = ?1) group by a.fileId order by count(a) desc")
    List<Object[]> mostUsedFiles(Integer ownerId, Pageable pageable);
}
