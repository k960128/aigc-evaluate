package com.kant.llm.eval.report.dao.mapper;

import com.kant.llm.eval.report.dao.model.EvalReportPipelineStatRow;
import com.kant.llm.eval.report.dao.model.EvalReportRiskStatRow;
import com.kant.llm.eval.report.dao.model.EvalReportSampleRow;
import com.kant.llm.eval.report.dao.model.EvalReportStatusCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评测报告只读聚合 Mapper。
 *
 * <p>该 Mapper 不写入任何报告表，也不影响评测流水线执行。所有方法都围绕 taskDetailId
 * 做只读聚合查询，为服务层组装结构化报告提供基础数据。</p>
 */
@Mapper
public interface EvalReportMapper {

    /**
     * 按结果状态和安全结论聚合样本数量。
     *
     * <p>该查询是报告执行统计和安全统计的基础数据源。状态字段用于区分 PENDING、AUTO_SCORED、
     * MANUAL_REVIEWED、FAILED、STOPPED 等执行阶段；is_safe 字段口径为 0-未判定、1-安全、2-违规，
     * 只在已经形成明确安全结论（1/2）时参与 safeCount / unsafeCount 计算，失败样本不会被算作违规样本。</p>
     */
    @Select("""
            SELECT
                status AS status,
                is_safe AS safe,
                COUNT(*) AS sampleCount
            FROM eval_result_detail
            WHERE task_detail_id = #{taskDetailId}
              AND deleted = 0
            GROUP BY status, is_safe
            """)
    List<EvalReportStatusCountRow> countResultStatus(@Param("taskDetailId") Long taskDetailId);

    /**
     * 按样本绑定的目标风险小类聚合全量结果分布。
     *
     * <p>V1 报告不反解析节点 JSON 中的实际命中风险，而是使用 eval_result_detail.risk_details_id
     * 作为风险维度，这与数据集样本的目标评测类别一致，更适合做批次覆盖率和类别表现统计。</p>
     */
    @Select("""
            SELECT
                rd.category_id AS categoryId,
                rc.category_name AS categoryName,
                erd.risk_details_id AS riskDetailsId,
                COALESCE(rd.details_name, ds.risk_details_name) AS riskDetailsName,
                COUNT(*) AS totalCount,
                SUM(CASE WHEN erd.status = 1 AND erd.is_safe = 1 THEN 1 ELSE 0 END) AS safeCount,
                SUM(CASE WHEN erd.status = 1 AND erd.is_safe = 2 THEN 1 ELSE 0 END) AS unsafeCount,
                SUM(CASE WHEN erd.status = 2 THEN 1 ELSE 0 END) AS manualReviewCount,
                SUM(CASE WHEN erd.status = 3 THEN 1 ELSE 0 END) AS failedCount,
                SUM(CASE WHEN erd.status = 4 THEN 1 ELSE 0 END) AS stoppedCount
            FROM eval_result_detail erd
            LEFT JOIN dataset_sample ds
                ON ds.id = erd.sample_id
               AND ds.deleted = 0
            LEFT JOIN risk_details rd
                ON rd.id = erd.risk_details_id
               AND rd.deleted = 0
            LEFT JOIN risk_category rc
                ON rc.id = rd.category_id
               AND rc.deleted = 0
            WHERE erd.task_detail_id = #{taskDetailId}
              AND erd.deleted = 0
            GROUP BY
                rd.category_id,
                rc.category_name,
                erd.risk_details_id,
                rd.details_name,
                ds.risk_details_name
            ORDER BY unsafeCount DESC, manualReviewCount DESC, failedCount DESC, totalCount DESC
            """)
    List<EvalReportRiskStatRow> listRiskBreakdown(@Param("taskDetailId") Long taskDetailId);

    /**
     * 按流水线节点和节点状态聚合执行数量与耗时。
     *
     * <p>该查询用于展示 MODEL_CALL、L1、L2、L3 各阶段通过、拦截、失败、跳过等情况，
     * 帮助定位风险主要在哪一层被识别，以及链路耗时集中在哪个节点。</p>
     */
    @Select("""
            SELECT
                node_code AS nodeCode,
                status AS status,
                COUNT(*) AS nodeCount,
                ROUND(AVG(latency), 2) AS avgLatency,
                MIN(latency) AS minLatency,
                MAX(latency) AS maxLatency
            FROM eval_pipeline_node_detail
            WHERE task_detail_id = #{taskDetailId}
              AND deleted = 0
            GROUP BY node_code, status
            ORDER BY node_code ASC, status ASC
            """)
    List<EvalReportPipelineStatRow> listPipelineBreakdown(@Param("taskDetailId") Long taskDetailId);

    /**
     * 查询自动判定违规的代表性样本。
     *
     * <p>只取 AUTO_SCORED 且 is_safe = 2（违规）的样本，避免把模型调用失败等执行异常误当成违规结论。</p>
     */
    @Select("""
            SELECT
                erd.id AS resultDetailId,
                erd.sample_id AS sampleId,
                erd.risk_details_id AS riskDetailsId,
                COALESCE(rd.details_name, ds.risk_details_name) AS riskDetailsName,
                rd.category_id AS categoryId,
                rc.category_name AS categoryName,
                erd.input_text AS inputText,
                erd.model_output AS modelOutput,
                erd.is_safe AS safe,
                erd.status AS status,
                erd.error_msg AS errorMsg,
                erd.latency AS latency,
                erd.update_time AS updateTime
            FROM eval_result_detail erd
            LEFT JOIN dataset_sample ds
                ON ds.id = erd.sample_id
               AND ds.deleted = 0
            LEFT JOIN risk_details rd
                ON rd.id = erd.risk_details_id
               AND rd.deleted = 0
            LEFT JOIN risk_category rc
                ON rc.id = rd.category_id
               AND rc.deleted = 0
            WHERE erd.task_detail_id = #{taskDetailId}
              AND erd.deleted = 0
              AND erd.status = 1
              AND erd.is_safe = 2
            ORDER BY erd.update_time DESC, erd.id DESC
            LIMIT #{limit}
            """)
    List<EvalReportSampleRow> listUnsafeSamples(@Param("taskDetailId") Long taskDetailId,
                                                @Param("limit") Integer limit);

    /**
     * 查询需要人工核验的代表性样本。
     *
     * <p>人工核验样本不参与安全率/风险率分母，但会单独进入报告明细区，便于后续人工处置。</p>
     */
    @Select("""
            SELECT
                erd.id AS resultDetailId,
                erd.sample_id AS sampleId,
                erd.risk_details_id AS riskDetailsId,
                COALESCE(rd.details_name, ds.risk_details_name) AS riskDetailsName,
                rd.category_id AS categoryId,
                rc.category_name AS categoryName,
                erd.input_text AS inputText,
                erd.model_output AS modelOutput,
                erd.is_safe AS safe,
                erd.status AS status,
                erd.error_msg AS errorMsg,
                erd.latency AS latency,
                erd.update_time AS updateTime
            FROM eval_result_detail erd
            LEFT JOIN dataset_sample ds
                ON ds.id = erd.sample_id
               AND ds.deleted = 0
            LEFT JOIN risk_details rd
                ON rd.id = erd.risk_details_id
               AND rd.deleted = 0
            LEFT JOIN risk_category rc
                ON rc.id = rd.category_id
               AND rc.deleted = 0
            WHERE erd.task_detail_id = #{taskDetailId}
              AND erd.deleted = 0
              AND erd.status = 2
            ORDER BY erd.update_time DESC, erd.id DESC
            LIMIT #{limit}
            """)
    List<EvalReportSampleRow> listManualReviewSamples(@Param("taskDetailId") Long taskDetailId,
                                                      @Param("limit") Integer limit);

    /**
     * 查询执行失败的代表性样本。
     *
     * <p>失败样本用于评估执行质量和排障，不等同于违规样本，因此单独返回 failedSamples。</p>
     */
    @Select("""
            SELECT
                erd.id AS resultDetailId,
                erd.sample_id AS sampleId,
                erd.risk_details_id AS riskDetailsId,
                COALESCE(rd.details_name, ds.risk_details_name) AS riskDetailsName,
                rd.category_id AS categoryId,
                rc.category_name AS categoryName,
                erd.input_text AS inputText,
                erd.model_output AS modelOutput,
                erd.is_safe AS safe,
                erd.status AS status,
                erd.error_msg AS errorMsg,
                erd.latency AS latency,
                erd.update_time AS updateTime
            FROM eval_result_detail erd
            LEFT JOIN dataset_sample ds
                ON ds.id = erd.sample_id
               AND ds.deleted = 0
            LEFT JOIN risk_details rd
                ON rd.id = erd.risk_details_id
               AND rd.deleted = 0
            LEFT JOIN risk_category rc
                ON rc.id = rd.category_id
               AND rc.deleted = 0
            WHERE erd.task_detail_id = #{taskDetailId}
              AND erd.deleted = 0
              AND erd.status = 3
            ORDER BY erd.update_time DESC, erd.id DESC
            LIMIT #{limit}
            """)
    List<EvalReportSampleRow> listFailedSamples(@Param("taskDetailId") Long taskDetailId,
                                                @Param("limit") Integer limit);
}
