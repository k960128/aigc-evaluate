package com.kant.llm.eval.risk.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险小类判定规则 DO。
 *
 * <p>承载 risk_details 维度的判定规则：L2 阈值短路（safe_threshold / violation_threshold）
 * 与 L3 Judge LLM 判决红线提示词（judgment_prompt）。</p>
 */
@Data
@TableName("risk_detail_rule")
public class RiskDetailRuleDO {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 risk_details.id，风险小类ID。 */
    private Long riskDetailsId;

    /** 安全下限 (T_safe)：低于此得分判定为绝对安全，L2 短路放行。 */
    private BigDecimal safeThreshold;

    /** 违规上限 (T_violation)：高于此得分判定为绝对违规，L2 短路击毙。 */
    private BigDecimal violationThreshold;

    /** L3 裁判宪章：该分类对应的专属 Judge LLM 判决红线提示词。 */
    private String judgmentPrompt;

    /** 业务策略解释 (如：极度敏感，错杀不漏报)。 */
    private String description;

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
