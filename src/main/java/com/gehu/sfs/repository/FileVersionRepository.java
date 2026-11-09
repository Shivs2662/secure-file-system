package com.gehu.sfs.repository;

import com.gehu.sfs.entity.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FileVersionRepository extends JpaRepository<FileVersion, Integer> {
    List<FileVersion> findByFileIdOrderByVersionNoDesc(Integer fileId);
    Optional<FileVersion> findByFileIdAndVersionNo(Integer fileId, Integer versionNo);
}
