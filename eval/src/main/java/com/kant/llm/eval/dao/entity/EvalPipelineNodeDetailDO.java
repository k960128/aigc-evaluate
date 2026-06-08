package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 评测流水线节点执行明细表 DO。
 *
 * <p>一条记录代表一次节点执行历史。为了保留重试、失败和短路过程，该表不按节点覆盖旧记录。</p>
 */
@TableName("eval_pipeline_node_detail")
@KeySequence("eval_pipeline_node_detail_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalPipelineNodeDetailDO {

    /** 主键ID */
    @TableId
    private Long id;

    /** 评测任务ID，对应 eval_task.id */
    private Long taskId;

    /** 任务执行批次ID，对应 eval_task_detail.id */
    private Long taskDetailId;

    /** 评测结果明细ID，对应 eval_result_detail.id */
    private Long resultDetailId;

    /** 数据集样本ID，对应 dataset_sample.id */
    private Long sampleId;

    /** 节点编码：MODEL_CALL、L1、L2、L3 */
    private String nodeCode;

    /** 节点状态：RUNNING、PASSED、BLOCKED、FAILED、SKIPPED、STOPPED */
    private String status;

    /** 节点开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 节点结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 节点耗时，单位毫秒 */
    private Integer latency;

    /** 节点错误信息或短路原因 */
    private String errorMsg;

    /** 节点输入快照，JSON 字符串 */
    private String inputSnapshot;

    /** 节点输出快照，JSON 字符串 */
    private String outputSnapshot;

    /** 节点结构化结果，JSON 字符串 */
    private String nodeResult;

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
