package com.gehu.sfs.repository;

import com.gehu.sfs.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Integer> {
    List<Folder> findByParentIdOrderByName(Integer parentId);
    List<Folder> findByOwnerIdOrderByName(Integer ownerId);
    boolean existsByParentIdAndNameAndOwnerId(Integer parentId, String name, Integer ownerId);
}
