package com.gehu.sfs.repository;

import com.gehu.sfs.entity.FileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Integer> {
    List<FileEntity> findByFolderIdOrderByName(Integer folderId);
    List<FileEntity> findByOwnerId(Integer ownerId);
    boolean existsByFolderIdAndName(Integer folderId, String name);

    /** SELECT ... FOR UPDATE on the file row: the mutex that makes lock acquisition atomic. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileEntity f where f.id = :id")
    Optional<FileEntity> lockById(@Param("id") Integer id);

    @Query("select f from FileEntity f where f.id in (select p.fileId from Permission p where p.userId = :uid) and f.ownerId <> :uid order by f.name")
    List<FileEntity> findSharedWith(@Param("uid") Integer userId);

    @Query("select coalesce(sum(f.sizeBytes),0) from FileEntity f where f.ownerId = :uid")
    long sumBytesByOwner(@Param("uid") Integer userId);

    @Query("select f.allocationType, count(f), coalesce(sum(f.sizeBytes),0) from FileEntity f group by f.allocationType")
    List<Object[]> statsByAllocation();
}
