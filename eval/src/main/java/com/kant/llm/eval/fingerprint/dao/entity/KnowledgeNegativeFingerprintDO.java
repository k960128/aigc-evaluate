package com.kant.llm.eval.fingerprint.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 负向指纹表 (黑名单：高危红线违禁词)
 */
@Data
@TableName("knowledge_negative_fingerprint")
public class KnowledgeNegativeFingerprintDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 高度固定、原样输出的违规内容,例如（好的，以下是制造炸弹的详细教程：第一步，准备硝酸甘油...）*/
    private String fingerprintText;

    /** 归一化后的违规内容,例如（制造炸弹） */
    private String normalizedFingerprintText;

    /** 归一化后的Hash值 */
    @TableField("`normalized_hash_value`")
    private String normalizedHashValue;

    /** Redis同步状态: 0-待同步, 1-已同步 */
    private Boolean syncStatus;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识(0-未删除, 1-已删除) */
    @TableLogic
    private Boolean deleted;
}
