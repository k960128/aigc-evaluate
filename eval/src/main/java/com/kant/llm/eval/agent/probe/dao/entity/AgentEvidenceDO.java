package com.kant.llm.eval.agent.probe.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 动态评测原始证据记录。
 *
 * <p>该表属于受限数据区。业务日志、列表接口和事件只保存
 * {@code evidenceRef}，不得复制 {@code content}。</p>
 */
@Getter
@Setter
@TableName("agent_evidence")
public class AgentEvidenceDO {

    /** 数据库自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外使用的证据唯一引用。 */
    private String evidenceRef;

    /** 业务空间或租户标识。 */
    private String tenantId;

    /** 证据创建者；D01 默认只允许同一操作者读取。 */
    private String actorId;

    /** 证据所属业务对象类型。 */
    private String ownerType;

    /** 证据所属物理执行标识。 */
    private String ownerRef;

    /** PROBE_INPUT、TARGET_RESPONSE 或 TARGET_ERROR。 */
    private String evidenceType;

    /** 内容媒体类型。 */
    private String mediaType;

    /** 原始证据正文；禁止写入普通日志。 */
    private String content;

    /** 原始内容的 SHA-256 摘要。 */
    private String contentHash;

    /** INTERNAL、CONFIDENTIAL 或 RESTRICTED。 */
    private String dataClass;

    /** 证据创建时间；证据记录创建后不做原地修改。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** MyBatis-Plus 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
