package com.chinatelecom.aigc.evaluate.service.scheduled;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chinatelecom.aigc.evaluate.common.enums.JudgeResultEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.TaskAnswerDO;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.TaskAnswerMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionDifficultyUpdateService {

    @Resource
    private TaskAnswerMapper taskAnswerMapper;

    @Resource
    private QuestionMapper questionMapper;

    /**
     * 每12小时执行一次，更新题目难度
     */
    @Scheduled(cron = "0 0 */12 * * ?")
    public void updateQuestionDifficultyBasedOnJudgment() {
        // 查询所有 task_answer 回答记录
        List<TaskAnswerDO> allAnswers = taskAnswerMapper.selectList(null);

        // 按 question_id（String类型）分组，只保留 judgeResult
        Map<String, List<Integer>> groupByQuestionId = allAnswers.stream()
                .collect(Collectors.groupingBy(
                        answer -> String.valueOf(answer.getQuestionId()),
                        Collectors.mapping(TaskAnswerDO::getJudgeResult, Collectors.toList())
                ));

        log.info("开始更新题目难度，共处理 {} 个题目", groupByQuestionId.size());

        for (Map.Entry<String, List<Integer>> entry : groupByQuestionId.entrySet()) {
            String questionIdStr = entry.getKey();
            List<Integer> judgeResults = entry.getValue();

            // 查询题目信息
            QuestionDO question = questionMapper.selectByQuestionId(questionIdStr);
            if (question == null) {
                log.debug("题目不存在，跳过更新，questionId={}", questionIdStr);
                continue;
            }

            String questionCategory = question.getCategory();

            // 计算不合格次数
            long unqualifiedCount = judgeResults.stream()
                    .filter(judgeResult -> "不合格".equals(
                            JudgeResultEnum.getQualifiedStatus(
                                    judgeResult != null ? judgeResult.intValue() : null, questionCategory)))
                    .count();

            // 判定新的难度等级
            String newDifficulty;
            if (unqualifiedCount >= 2) {
                newDifficulty = QuestionDifficultyEnum.HARD.name();
            } else if (unqualifiedCount == 1) {
                newDifficulty = QuestionDifficultyEnum.MEDIUM.name();
            } else {
                newDifficulty = QuestionDifficultyEnum.SIMPLE.name();
            }

            // 当前题目难度
            String currentDifficulty = question.getDifficulty();

            // 如果难度不同则更新
            if (!newDifficulty.equalsIgnoreCase(currentDifficulty)) {
                UpdateWrapper<QuestionDO> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("question_id", questionIdStr)
                        .set("difficulty", newDifficulty);
                int rows = questionMapper.update(null, updateWrapper);

                if (rows > 0) {
                    log.debug("题目 {} 难度从 {} 更新为: {}", questionIdStr, currentDifficulty, newDifficulty);
                } else {
                    log.debug("题目 {} 难度更新失败", questionIdStr);
                }
            }
        }
    }
}
