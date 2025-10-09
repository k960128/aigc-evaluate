package com.chinatelecom.aigc.evaluate.designhandler.chain;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConfDifficulty;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractStep;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 自定义难度
 */
@Slf4j
@Component
public class GenerateQuestionDifficultyChainHandler implements GenerateQuestionChainFilter<ExtractStep> {

    private final QuestionMapper questionMapper;
    private final QuestionSetItemMapper questionSetItemMapper;
    private Map<String, List<QuestionDO>> questionMap;
    private List<QuestionSetItemDO> questionSetItemDOList;

    public GenerateQuestionDifficultyChainHandler(QuestionMapper questionMapper,
                                                  QuestionSetItemMapper questionSetItemMapper) {
        this.questionMapper = questionMapper;
        this.questionSetItemMapper = questionSetItemMapper;
    }

    @Override
    public void handler(ExtractStep requestParam) {
        log.info("自定义难度责任链执行逻辑,题库：{},题解ID：{}", requestParam.getCategory(), requestParam.getQuestionSetId());

        // 1.获取配置信息
        ExtractConfDifficulty difficultyConf = requestParam.getDifficultyConf();
        if (ObjectUtil.isNull(difficultyConf)) {
            return;
        }
        // 2.获取自定义难度题目抽取总数量
        Integer difficultyCount = difficultyConf.getDifficultyCount();
        if (ObjectUtil.isNull(difficultyCount) || difficultyCount == 0) {
            log.info("{}自定义难度责任链执行逻辑，总题目数量：{}", requestParam.getCategory(), difficultyCount);
            return;
        }
        // 2.1 获取自定义难度题目并转换成map
        questionMap = questionMapper.selectList().stream()
                .filter(f -> f.getCategory().equals(requestParam.getCategory()))
                .filter(f -> !requestParam.getQuestionIdSet().contains(f.getQuestionId()))
                .collect(Collectors.groupingBy(QuestionDO::getDifficulty));
        // 2.2 初始化保存集合对象
        questionSetItemDOList = new ArrayList<>();
        // 3.生成题目
        Integer simpleCount = generateSimple(requestParam.getQuestionIdSet(), difficultyConf, requestParam.getQuestionSetId());
        Integer mediumCount = generateMedium(requestParam.getQuestionIdSet(), difficultyConf, requestParam.getQuestionSetId());
        Integer hardCount = generateHard(requestParam.getQuestionIdSet(), difficultyConf, requestParam.getQuestionSetId());
        // 4.批量存储
        Integer rowCount = batchSave();
        log.info("{}自定义难度责任链执行逻辑，计划-总题目数量：{},simple:{},medium:{},hard:{},实际-总题目数量:{},simple:{},medium:{},hard:{}",
                requestParam.getCategory(),
                difficultyCount,
                difficultyConf.getSimpleCount(),
                difficultyConf.getMediumCount(),
                difficultyConf.getHardCount(),
                rowCount,
                simpleCount,
                mediumCount,
                hardCount);
    }

    @Override
    public int getOrder() {
        return 20;
    }


    private Integer generateSimple(Set<String> questionIdSet,
                                   ExtractConfDifficulty difficultyConf,
                                   Long questionSetId) {
        Integer simpleCount = difficultyConf.getSimpleCount();
        if (simpleCount > 0) {
            AtomicInteger count = new AtomicInteger(0);
            // 判断是否存在数据
            if (questionMap.containsKey(QuestionDifficultyEnum.SIMPLE.name())) {
                if (questionMap.get(QuestionDifficultyEnum.SIMPLE.name()).size() <= simpleCount) {
                    questionMap.get(QuestionDifficultyEnum.SIMPLE.name()).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                } else {
                    // 随机打乱
                    Collections.shuffle(questionMap.get(QuestionDifficultyEnum.SIMPLE.name()));
                    questionMap.get(QuestionDifficultyEnum.SIMPLE.name()).subList(0, simpleCount).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                }
                return count.get();
            }
        }
        return 0;
    }

    private Integer generateMedium(Set<String> questionIdSet,
                                   ExtractConfDifficulty difficultyConf,
                                   Long questionSetId) {
        Integer mediumCount = difficultyConf.getMediumCount();
        if (mediumCount > 0) {
            AtomicInteger count = new AtomicInteger(0);
            // 判断是否存在数据
            if (questionMap.containsKey(QuestionDifficultyEnum.MEDIUM.name())) {
                if (questionMap.get(QuestionDifficultyEnum.MEDIUM.name()).size() <= mediumCount) {
                    questionMap.get(QuestionDifficultyEnum.MEDIUM.name()).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                } else {
                    // 随机打乱
                    Collections.shuffle(questionMap.get(QuestionDifficultyEnum.MEDIUM.name()));
                    questionMap.get(QuestionDifficultyEnum.MEDIUM.name()).subList(0, mediumCount).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                }
            }
            return count.get();
        }
        return 0;
    }

    private Integer generateHard(Set<String> questionIdSet,
                                 ExtractConfDifficulty difficultyConf,
                                 Long questionSetId) {
        Integer hardCount = difficultyConf.getHardCount();
        if (hardCount > 0) {
            AtomicInteger count = new AtomicInteger(0);
            //判断是否存在数据
            if (questionMap.containsKey(QuestionDifficultyEnum.HARD.name())) {
                if (questionMap.get(QuestionDifficultyEnum.HARD.name()).size() <= hardCount) {
                    questionMap.get(QuestionDifficultyEnum.HARD.name()).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                } else {
                    // 随机打乱
                    Collections.shuffle(questionMap.get(QuestionDifficultyEnum.HARD.name()));
                    questionMap.get(QuestionDifficultyEnum.HARD.name()).subList(0, hardCount).forEach(questionDO -> {
                        QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                        questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                        questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                        questionSetItemDO.setQuestionSetId(questionSetId);
                        questionSetItemDOList.add(questionSetItemDO);
                        // 收集题目ID集合
                        questionIdSet.add(questionDO.getQuestionId());
                        count.getAndIncrement();
                    });
                }
                return count.get();
            }
        }
        return 0;
    }

    @Override
    public Integer batchSave() {
        if (CollectionUtil.isNotEmpty(questionSetItemDOList)) {
            int count = questionSetItemDOList.size();
            questionSetItemMapper.insertBatch(questionSetItemDOList);
            // 清空集合
            questionSetItemDOList.clear();
            return count;
        }
        return 0;
    }
}
