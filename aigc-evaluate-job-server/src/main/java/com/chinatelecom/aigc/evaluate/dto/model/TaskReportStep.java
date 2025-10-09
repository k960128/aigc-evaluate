package com.chinatelecom.aigc.evaluate.dto.model;

import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 生成PDF报告入参模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskReportStep {
    private String userId;
    private String taskId;
    private String modelId;
    private String reportType;
    private String name;
    JobResp jobInfo;
}