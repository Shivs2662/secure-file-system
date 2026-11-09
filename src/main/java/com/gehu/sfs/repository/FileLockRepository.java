package com.gehu.sfs.repository;

import com.gehu.sfs.entity.FileLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileLockRepository extends JpaRepository<FileLock, Integer> {
    Optional<FileLock> findFirstByFileIdAndExpiresAtAfter(Integer fileId, LocalDateTime now);
    List<FileLock> findByExpiresAtAfterOrderByAcquiredAtDesc(LocalDateTime now);

    @Modifying
    @Query("delete from FileLock l where l.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from FileLock l where l.fileId = :fid")
    void deleteByFile(@Param("fid") Integer fileId);
}
