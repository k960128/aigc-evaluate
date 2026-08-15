package com.kant.llm.eval.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参与 AC 快照发布的 JVM 节点运行注册信息。
 */
@Data
@TableName("ac_snapshot_node_registry")
public class AcSnapshotNodeRegistryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nodeId;

    private String appName;

    private String host;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeatTime;

    private Long currentVersion;

    private Long preparedVersion;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
