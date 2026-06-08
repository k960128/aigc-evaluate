package com.kant.llm.eval.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.dto.req.EvalResultDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskPageRequest;
import com.kant.llm.eval.dto.resp.EvalPipelineNodeDetailVO;
import com.kant.llm.eval.dto.resp.EvalResultDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskStatusVO;
import com.kant.llm.eval.dto.resp.EvalTaskVO;

import java.util.List;

public interface EvalTaskService {
    void createEvalTask(CreateEvalTaskRequest request);

    Boolean submitEvalTask(Long taskId);

    /**
     * 终止当前评测任务的活跃执行批次。
     *
     * <p>该操作只终止当前批次，不删除已完成结果，也不支持恢复；后续重新评测需要再次提交任务。</p>
     *
     * @param taskId 评测任务 ID
     * @return 是否成功处理停止请求
     */
    Boolean stopEvalTask(Long taskId);

    /**
     * 分页查询评测任务定义列表。
     *
     * @param request 评测任务分页查询请求
     * @return 评测任务分页响应
     */
    Page<EvalTaskVO> pageEvalTask(EvalTaskPageRequest request);

    /**
     * 分页查询指定评测任务的执行批次列表。
     *
     * @param request 执行批次分页查询请求
     * @return 执行批次分页响应
     */
    Page<EvalTaskDetailVO> pageEvalTaskDetail(EvalTaskDetailPageRequest request);

    /**
     * 分页查询评测结果明细列表。
     *
     * @param request 评测结果明细分页查询请求
     * @return 评测结果明细分页响应
     */
    Page<EvalResultDetailVO> pageEvalResultDetail(EvalResultDetailPageRequest request);

    /**
     * 查询评测任务当前活跃批次状态；没有活跃批次时返回最近一次批次状态。
     *
     * @param taskId 评测任务 ID
     * @return 评测任务状态响应
     */
    EvalTaskStatusVO getEvalTaskStatus(Long taskId);

    /**
     * 查询单条样本结果的流水线节点日志。
     *
     * @param resultDetailId 评测结果明细 ID
     * @return 流水线节点日志列表
     */
    List<EvalPipelineNodeDetailVO> listPipelineNodeLogs(Long resultDetailId);

    List<EvalTaskDetailDO> selectPendingTasks();
}
