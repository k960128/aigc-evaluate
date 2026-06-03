package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 风险场景明细表(规则父分片)
 */
@Data
@TableName("risk_scenario")
public class RiskScenarioDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 直属的5大类ID (与31小类解耦) */
    private Long majorCategoryId;

    /** 场景全局唯一编码 (如: SCN-001) */
    private String scenarioCode;

    /** 场景名称 (如: 虚拟货币与庞氏骗局诱导) */
    private String scenarioName;

    /** 裁判系统指令(System Prompt)，包含绝对红线与豁免条件 */
    private String judgeRule;

    /** 严重等级: 1-低, 2-中, 3-高, 4-致命 */
    private Integer severityLevel;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

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

    /** 是否删除 0未删除 1删除 */
    @TableLogic
    private Boolean deleted;
}