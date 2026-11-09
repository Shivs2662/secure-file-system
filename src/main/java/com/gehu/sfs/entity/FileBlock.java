package com.gehu.sfs.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.gehu.sfs.entity.Enums.*;

@Entity
@Table(name = "File_Blocks")
@Getter @Setter
public class FileBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_row_id") private Integer id;
    @Column(name = "file_id", nullable = false) private Integer fileId;
    @Column(name = "block_no", nullable = false) private Integer blockNo;
    @Column(name = "sequence_no") private Integer sequenceNo;
    @Column(name = "next_block") private Integer nextBlock;
    @Column(name = "is_index_block", nullable = false) private boolean indexBlock;
}
