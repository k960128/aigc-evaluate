package com.chinatelecom.aigc.evaluate.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.TaskAnswerDO;
import com.chinatelecom.aigc.evaluate.dto.req.TaskAnswerPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.TaskAnswerSaveReq;
import com.chinatelecom.aigc.evaluate.dto.resp.EvaluateResultStatisticsGroupResp;
import com.chinatelecom.aigc.evaluate.dto.resp.EvaluateResultStatisticsResp;
import com.chinatelecom.aigc.evaluate.dto.resp.TaskAnswerAbnormalResp;
import com.chinatelecom.aigc.evaluate.dto.resp.TaskAnswerPageResp;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface TaskAnswerService {
    /**
     * 创建任务答案
     * @param taskId 任务 ID
     * @param questionId 题目 ID
     * @param appName 应用名称
     * @param modelName 模型名称
     * @param modelVersion 模型版本
     * @param answerContent 答案内容
     * @param questionCategory 所属题库
     * @param judgeResult 人工审核结果
     * @param questionSet 习题集
     * @return 创建的 TaskAnswerDO
     */
    TaskAnswerDO createDo(Long taskId, Long modelId, String questionId, Integer questionVersion, String appName, String modelName,
                          String modelVersion, String questionContent, String answerContent, String questionCategory, Integer judgeResult, Long questionSet);

    /**
     * 创建任务答案
     * @param req 任务答案请求参数
     * @return 创建的任务答案
     */
    TaskAnswerDO create(TaskAnswerSaveReq req);

    /**
     * 根据任务答案 ID 获取任务答案
     * @param taskAnswerId 任务答案 ID
     * @return 任务答案详情
     */
    TaskAnswerDO getByTaskAnswerId(String taskAnswerId);

    /**
     * 删除任务答案
     * @param taskAnswerId 任务答案 ID
     * @return 影响的行数
     */
    int delete(String taskAnswerId);

    /**
     * 根据任务id删除任务答案
     * @param taskId 任务 ID
     */
    void batchDeleteByTaskId(Long taskId);

    /**
     * 分页查询任务答案
     * @param taskAnswerPageReq 任务答案分页请求
     * @return 任务答案分页数据
     */
    PageResult<TaskAnswerDO> getTaskAnswerPage(TaskAnswerPageReq taskAnswerPageReq);

    /**
     * 分页查询任务答案
     * @param pageReqVO 任务答案分页请求
     * @return 任务答案分页数据
     */
    public PageResult<TaskAnswerPageResp> getTaskAnswerAllPage(TaskAnswerPageReq pageReqVO);

    /**
     * 对评测集进行人工标注
     * @param judgeResult 评判结果
     * @param id id
     * @return
     */
    void updateJudgeResultById(Long judgeResult, Long id);

    /**
     * 评测结果统计
     * @param taskId 任务id
     * @param modelId 模型 ID
     * @return 统计结果
     */
    EvaluateResultStatisticsGroupResp getEvaluateResultStatisticsByTaskIdAndModelId(Long taskId, Long modelId);

    /**
     * 评测结果统计
     * @param taskId 任务id
     * @return 统计结果
     */
    Map<Long, EvaluateResultStatisticsGroupResp> getEvaluateResultStatisticsByTaskId(Long taskId);

    /**
     * 评测结果统计
     * @param taskId 任务id
     * @return 统计结果
     */
    Map<Long, EvaluateResultStatisticsGroupResp> getEvaluateResultStatisticsGroupByQuestionId(Long taskId, Long modelId);

    /**
     * 评测结果统计
     * @param taskId 任务id
     * @param modelId 模型id
     * @return 统计结果
     */
    Map<Long, EvaluateResultStatisticsGroupResp> getEvaluateResultStatisticsByQuestionId(Long taskId, Long modelId);

    /**
     * 评测结果统计
     * @param taskId 任务id
     * @param modelId 模型 ID
     * @return
     */
    boolean autoJudgment(Long taskId, Long modelId);

    /**
     * 同步机审结果到人工审查结果
     * @param taskId 任务id
     * @param modelId 模型 ID
     * @return
     */
    boolean syncMachineJudgmentToManualReview(Long taskId, Long modelId);

    /*
        @Override
        public List<QuestionSetItemResp> list(List<Long> ids) {
            List<QuestionSetItemResp> respList = new ArrayList<>();
            // 先查出所有题集
            List<QuestionSetInfoDO> questionSetInfoDOList = questionSetInfoMapper.selectList(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                    .in(QuestionSetInfoDO::getId, ids));
            if (CollectionUtil.isNotEmpty(questionSetInfoDOList)) {
                Map<Long, List<QuestionSetInfoDO>> questionSetByIdMap = questionSetInfoDOList.stream().collect(Collectors.groupingBy(QuestionSetInfoDO::getId));
                // 查询映射习题
                List<QuestionSetItemDO> questionSetItemDOList = questionSetItemMapper.selectListByQuestionSetIds(questionSetInfoDOList.stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()));
                // 查出题目
                Map<String, List<QuestionDO>> questionByQuestionIdMap =
                        questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));
                // 组装数据
                questionSetItemDOList.forEach(itemDO -> {
                    QuestionSetItemResp questionSetItemResp = new QuestionSetItemResp();
                    questionSetItemResp.setId(itemDO.getId());
                    questionSetItemResp.setQuestionSetId(itemDO.getQuestionSetId());
                    questionSetItemResp.setQuestionSetName(questionSetByIdMap.get(itemDO.getQuestionSetId()).get(0).getQuestionSetName());
                    questionSetItemResp.setQuestionId(itemDO.getQuestionId());
                    questionSetItemResp.setQuestionVersion(itemDO.getQuestionVersion());
                    questionSetItemResp.setTitle(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTitle());
                    questionSetItemResp.setCategory(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getCategory());
                    questionSetItemResp.setTags(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTags());
                    questionSetItemResp.setDifficulty(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getDifficulty());
                    questionSetItemResp.setAttackMethod(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getAttackMethod());
                    respList.add(questionSetItemResp);
                });
            }
            return respList;
        }
     */

    /**
     * 获取模型在任务下的违规数量
     * @param category 题库
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return 数量
     */
    Integer getTaskAnswerByAbnormalViolationCount(String category, Long taskId, Long modelId);

    /**
     * 获取违规内容，并按照正负向题库分类
     * @param taskId 任务ID
     * @param modelId 模型ID
     * @return
     */
    Map<String, List<TaskAnswerAbnormalResp>> getTaskAnswerByAbnormalViolation(Long taskId, Long modelId);

    /**
     * 分页查询异常题目
     *
     * @param currentPage 当前页
     * @param pageSize 页大小
     * @param category 所属题库
     * @return 分页数据
     */
    Page<TaskAnswerAbnormalResp> pageQueryTaskAnswerByAbnormalViolation(int currentPage, int pageSize, Long taskId, Long modelId, @Nullable String category);

    /**
     * 分页查询异常题目
     *
     * @param currentPage 当前页
     * @param pageSize 页大小
     * @param questionSetId 习题集ID
     * @return 分页数据
     */
    Page<TaskAnswerAbnormalResp> pageQueryTaskAnswerByQuestionSetId(int currentPage, int pageSize, Long taskId, Long modelId, Long questionSetId);
}

