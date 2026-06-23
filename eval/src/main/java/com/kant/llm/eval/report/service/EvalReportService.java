package com.kant.llm.eval.report.service;

import com.kant.llm.eval.report.dto.resp.EvalTaskReportVO;

/**
 * 评测报告应用服务。
 *
 * <p>服务层负责校验批次、读取聚合数据、计算报告口径，并组装前端可直接渲染的报告 VO。</p>
 */
public interface EvalReportService {

    /**
     * 生成指定执行批次的结构化评测报告。
     *
     * <p>报告采用“全量统计 + 问题样本明细”口径：所有样本都会参与统计汇总，
     * 但明细区只返回违规、人工核验和失败样本。运行中的批次也可以调用该方法，
     * 此时报告表示当前时刻的数据快照。</p>
     *
     * @param taskDetailId 评测任务执行批次 ID
     * @param topN 问题样本返回数量，服务层会限制默认值和最大值
     * @return 结构化评测报告
     */
    EvalTaskReportVO getTaskReport(Long taskDetailId, Integer topN);
}
