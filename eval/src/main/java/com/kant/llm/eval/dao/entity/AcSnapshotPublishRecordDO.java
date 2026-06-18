package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AC 快照发布生命周期记录。
 */
@Data
@TableName("ac_snapshot_publish_record")
public class AcSnapshotPublishRecordDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private String manifestKey;

    private String globalHash;

    private Integer wordCount;

    private String status;

    private Integer targetNodeCount;

    private String targetNodeIds;

    private Integer successNodeCount;

    private String lastError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
