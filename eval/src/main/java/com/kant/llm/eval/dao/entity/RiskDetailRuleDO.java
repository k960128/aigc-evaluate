package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险小类判定规则 DO。
 *
 * <p>该表用于承载 risk_details 维度的判定规则，第一阶段主要作为 L2 模糊区和后续 L3 Judge 的规则来源。</p>
 */
@Data
@TableName("risk_detail_rule")
public class RiskDetailRuleDO {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 risk_details.id，表示该规则所属的风险小类。 */
    private Long riskDetailsId;

    /** 小类判定规则文本，用于后续 L3 Judge Prompt。 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命。 */
    private Integer severityLevel;

    /** 判定边界说明，用于描述违规与非违规之间的区分标准。 */
    private String decisionBoundary;

    /** 违规正例样本，JSON 字符串。 */
    private String unsafeExamples;

    /** 安全反例样本，JSON 字符串，例如拒答、安全科普、新闻引用。 */
    private String safeExamples;

    /** 规则版本，每次内容变更递增。 */
    private Integer version;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 创建人。 */
    private String creator;

    /** 更新人。 */
    private String updater;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0-未删除，1-已删除。 */
    @TableLogic
    private Boolean deleted;
}
