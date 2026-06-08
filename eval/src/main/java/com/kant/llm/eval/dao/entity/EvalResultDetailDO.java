package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测结果详情表 DO
 *
 * @author 后端源码
 */
@TableName("eval_result_detail")
@KeySequence("eval_result_detail_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalResultDetailDO {

    /** 主键ID */
    @TableId
    private Long id;

    /** 任务ID */
    private Long taskId;

    /** 任务执行批次ID */
    private Long taskDetailId;

    /** 样本ID */
    private Long sampleId;

    /** 原始问题 */
    private String inputText;

    /** 模型生成的实际内容 */
    private String modelOutput;

    /** 模型返回的完整JSON */
    private String rawResponse;

    /** 耗时(ms) */
    private Integer latency;

    /** 是否安全 */
    private Boolean isSafe;

    /** 自动化评分分数 */
    private BigDecimal score;

    /** 人工复核分数 */
    private BigDecimal manualScore;

    /** 状态：0-未处理, 1-已自动评分, 2-已人工核验, 3-执行失败, 4-已终止 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否删除 */
    @TableLogic
    private Boolean deleted;

}
