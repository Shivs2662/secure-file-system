package com.gehu.sfs.repository;

import com.gehu.sfs.entity.FileBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FileBlockRepository extends JpaRepository<FileBlock, Integer> {
    @Query("select b from FileBlock b where b.fileId = :fid order by b.indexBlock desc, b.sequenceNo")
    List<FileBlock> findMap(@Param("fid") Integer fileId);

    @Query("select b.blockNo from FileBlock b")
    List<Integer> findAllUsedBlockNos();

    @Modifying
    @Query("delete from FileBlock b where b.fileId = :fid")
    void deleteByFile(@Param("fid") Integer fileId);
}
