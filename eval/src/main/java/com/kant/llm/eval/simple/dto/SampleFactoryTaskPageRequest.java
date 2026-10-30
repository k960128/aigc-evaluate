package com.kant.llm.eval.simple.dto;

import lombok.Data;

/**
 * 样本扩充任务分页查询请求
 */
@Data
public class SampleFactoryTaskPageRequest {

    /** 当前页码，未传或小于1时默认第1页 */
    private Integer current;

    /** 每页数量，未传或小于1时默认10条 */
    private Integer size;
}
