package com.kant.llm.eval.sample.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SampleFactoryTaskExecutionDetailPageRequest {

    /**
     * 样本生产任务ID，对应任务表的task_id。
     */
    @NotBlank(message = "样本生产任务ID不能为空")
    private String taskId;

    /**
     * 当前页，小于等于0或为空时默认为1。
     */
    private Integer current;

    /**
     * 每页条数，小于等于0或为空时默认为10。
     */
    private Integer size;
}
