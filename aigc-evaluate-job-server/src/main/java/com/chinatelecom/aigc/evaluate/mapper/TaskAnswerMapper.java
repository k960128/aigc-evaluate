package com.chinatelecom.aigc.evaluate.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinatelecom.aigc.evaluate.common.enums.JudgeResultEnum;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.TaskAnswerDO;
import com.chinatelecom.aigc.evaluate.dto.req.TaskAnswerPageReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.mapper.BaseMapperX;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.enums.JudgeResultEnum.ANSWERED;
import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

@Mapper
public interface TaskAnswerMapper extends BaseMapperX<TaskAnswerDO> {
    /**
     * 分页查询任务答案
     * @param reqVO 查询参数
     * @return 分页结果
     */
    default PageResult<TaskAnswerDO> selectPage(TaskAnswerPageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TaskAnswerDO>()
                .eqIfPresent(TaskAnswerDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(TaskAnswerDO::getModelId, reqVO.getModelId())
                .eqIfPresent(TaskAnswerDO::getQuestionId, reqVO.getQuestionId())
                .eqIfPresent(TaskAnswerDO::getQuestionCategory, reqVO.getQuestionCategory())
                .likeIfPresent(TaskAnswerDO::getQuestionContent, reqVO.getQuestionContent())
                .orderByAsc(TaskAnswerDO::getId)
        );
    }


    @Select("SELECT model_id, judge_result, question_category, COUNT(*) as count " +
            "FROM task_answer " +
            "WHERE task_id = #{taskId} AND deleted = 0 " +
            "GROUP BY model_id, judge_result, question_category")
    List<Map<String, Object>> countStatusByTaskIdManual(@Param("taskId") Long taskId);

    @Select("SELECT model_id, question_set, judge_result, question_category, COUNT(*) as count " +
            "FROM task_answer " +
            "WHERE task_id = #{param1} AND model_id = #{param2} AND deleted = 0 " +
            "GROUP BY question_set, judge_result, question_category")
    List<Map<String, Object>> countStatusByQuestionIdManual(Long taskId, Long modelId);

    @Select("SELECT model_id, violation, question_category, COUNT(*) as count " +
            "FROM task_answer " +
            "WHERE task_id = #{taskId} AND deleted = 0 " +
            "GROUP BY model_id, violation, question_category")
    List<Map<String, Object>> countStatusByTaskIdAuto(@Param("taskId") Long taskId);

    @Select("SELECT model_id, question_set, violation, question_category, COUNT(*) as count " +
            "FROM task_answer " +
            "WHERE task_id = #{param1} AND model_id = #{param2} AND deleted = 0 " +
            "GROUP BY question_set, violation, question_category")
    List<Map<String, Object>> countStatusByQuestionIdAuto(Long taskId, Long modelId);

    default void updateJudgeResultById(Long judgeResult, Long id) {
        if (selectById(id) == null) {
            throw exception(TASK_ANSWER_NOT_EXISTS_ERROR);
        }

        int rows = update(null, new LambdaUpdateWrapper<TaskAnswerDO>()
                .eq(TaskAnswerDO::getId, id)
                .set(TaskAnswerDO::getJudgeResult, judgeResult));

        if (rows == 0) {
            throw exception(TASK_ANSWER_UPDATE_ERROR);
        }
    }

    default List<TaskAnswerDO> getTaskAnswerByAbnormalViolation(Long taskId, Long modelId) {
        List<Integer> btw = new ArrayList<Integer>() {{
            add(JudgeResultEnum.UNANSWERED.getStatus());
            add(JudgeResultEnum.GENERATED_INAPPROPRIATE_CONTENT.getStatus());
            add(JudgeResultEnum.NOT_REJECTED.getStatus());
        }};
        return selectList(new LambdaQueryWrapperX<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .in(TaskAnswerDO::getJudgeResult, btw));
    }

    default Integer getTaskAnswerByAbnormalViolationCount(String category, Long taskId, Long modelId) {
        List<Integer> btw = new ArrayList<Integer>() {{
            add(JudgeResultEnum.UNANSWERED.getStatus());
            add(JudgeResultEnum.GENERATED_INAPPROPRIATE_CONTENT.getStatus());
            add(JudgeResultEnum.NOT_REJECTED.getStatus());
        }};
        return Math.toIntExact(selectCount(new LambdaQueryWrapperX<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .eq(TaskAnswerDO::getQuestionCategory, category)
                .in(TaskAnswerDO::getJudgeResult, btw)));
    }

    default Page<TaskAnswerDO> pageQueryTaskAnswerByAbnormalViolation(int currentPage, int pageSize, Long taskId, Long modelId, String category) {
        List<Integer> btw = new ArrayList<Integer>() {{
            add(JudgeResultEnum.UNANSWERED.getStatus());
            add(JudgeResultEnum.GENERATED_INAPPROPRIATE_CONTENT.getStatus());
            add(JudgeResultEnum.NOT_REJECTED.getStatus());
        }};
        return selectPage(new Page<>(currentPage, pageSize), new LambdaQueryWrapperX<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .eq(TaskAnswerDO::getQuestionCategory, category)
                .in(TaskAnswerDO::getJudgeResult, btw)
                .orderByAsc(TaskAnswerDO::getId));

    }

    default List<TaskAnswerDO> listTaskAnswerByAbnormalViolationLimitCount(Long taskId, Long modelId, String category, int count) {
        List<Integer> btw = Arrays.asList(
                JudgeResultEnum.UNANSWERED.getStatus(),
                JudgeResultEnum.GENERATED_INAPPROPRIATE_CONTENT.getStatus(),
                JudgeResultEnum.NOT_REJECTED.getStatus()
        );

        LambdaQueryWrapper<TaskAnswerDO> wrapper = new LambdaQueryWrapper<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .in(TaskAnswerDO::getJudgeResult, btw)
                .orderByAsc(TaskAnswerDO::getId);

        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(TaskAnswerDO::getQuestionCategory, category);
        }

        return selectPage(new Page<>(1, count), wrapper).getRecords();
    }




    default List<TaskAnswerDO> queryAllTaskAnswers(Long taskId, Long modelId) {
        return selectList(new LambdaQueryWrapperX<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .eq(TaskAnswerDO::getDeleted, 0)
                .orderByAsc(TaskAnswerDO::getId));
    }

    default List<TaskAnswerDO> queryTaskAnswersByPage(Long taskId, Long modelId, int offset, int limit) {
        int pageNum = offset / limit + 1; // 计算页码，从1开始

        Page<TaskAnswerDO> page = new Page<>(pageNum, limit);

        LambdaQueryWrapper<TaskAnswerDO> queryWrapper = new LambdaQueryWrapper<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .eq(TaskAnswerDO::getDeleted, 0)
                .orderByAsc(TaskAnswerDO::getId);

        Page<TaskAnswerDO> resultPage = selectPage(page, queryWrapper);

        return resultPage.getRecords();
    }

    default Page<TaskAnswerDO> pageQueryTaskAnswerByQuestionSetId(int currentPage, int pageSize, Long taskId, Long modelId, Long questionSetId) {
        /*
        List<Integer> btw = new ArrayList<Integer>() {{
            add(JudgeResultEnum.UNJUDGED.getStatus());
            add(JudgeResultEnum.UNCERTAIN.getStatus());
            add(JudgeResultEnum.ANSWERED.getStatus());
            add(JudgeResultEnum.UNANSWERED.getStatus());
            add(JudgeResultEnum.GENERATED_INAPPROPRIATE_CONTENT.getStatus());
            add(JudgeResultEnum.NOT_REJECTED.getStatus());
            add(JudgeResultEnum.REJECTED.getStatus());
            add(JudgeResultEnum.POSITIVE_GUIDANCE.getStatus());
        }};
         */
        return selectPage(new Page<>(currentPage, pageSize), new LambdaQueryWrapperX<TaskAnswerDO>()
                .eq(TaskAnswerDO::getTaskId, taskId)
                .eq(TaskAnswerDO::getModelId, modelId)
                .eq(TaskAnswerDO::getQuestionSet, questionSetId)
                //.in(TaskAnswerDO::getJudgeResult, btw)
                .orderByAsc(TaskAnswerDO::getId));

    }
}
