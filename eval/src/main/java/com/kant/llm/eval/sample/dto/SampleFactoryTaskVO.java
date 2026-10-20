package com.kant.llm.eval.sample.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本扩充任务响应对象
 */
@Data
public class SampleFactoryTaskVO {

    /** 主键ID */
    private Long id;

    /** 样本生产任务ID */
    private String taskId;

    /** 变异战术矩阵参数，JSON 字符串 */
    private String mutationTacticsConf;

    /** 单批迭代引擎参数，JSON 字符串 */
    private String engineParamsConf;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;
}
