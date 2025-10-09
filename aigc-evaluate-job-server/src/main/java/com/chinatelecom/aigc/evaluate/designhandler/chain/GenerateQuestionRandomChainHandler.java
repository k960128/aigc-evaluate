package com.chinatelecom.aigc.evaluate.designhandler.chain;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConfRandom;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractStep;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 随机抽取题目链
 */
@Slf4j
@Component
public class GenerateQuestionRandomChainHandler implements GenerateQuestionChainFilter<ExtractStep> {

    private List<QuestionSetItemDO> questionSetItemDOList;
    private final QuestionMapper questionMapper;
    private final QuestionSetItemMapper questionSetItemMapper;

    public GenerateQuestionRandomChainHandler(QuestionMapper questionMapper,
                                              QuestionSetItemMapper questionSetItemMapper) {
        this.questionMapper = questionMapper;
        this.questionSetItemMapper = questionSetItemMapper;
    }

    @Override
    public void handler(ExtractStep requestParam) {
        log.info("随机责任链执行逻辑,题库：{},题解ID：{}", requestParam.getCategory(), requestParam.getQuestionSetId());
        // 1.获取配置信息
        ExtractConfRandom randomConf = requestParam.getRandomConf();
        if(ObjectUtil.isNull(randomConf)){
            return;
        }
        // 2.获取随机题目抽取总数量
        Integer randomCount = randomConf.getRandomCount();
        Set<String> questionIdSet = requestParam.getQuestionIdSet();
        if (randomCount > 0) {
            // 2.1 获取题目并排除已存在的题目
            List<QuestionDO> questionDOList = questionMapper.selectList().stream()
                    .filter(f -> f.getCategory().equals(requestParam.getCategory()))
                    .filter(f -> !questionIdSet.contains(f.getQuestionId()))
                    .collect(Collectors.toList());
            // 2.2 初始化保存集合对象
            questionSetItemDOList = new ArrayList<>();

            // 3. 判断题目数量
            if (questionDOList.size() > randomCount) {
                // 打乱顺序
                Collections.shuffle(questionDOList);
                questionDOList = questionDOList.subList(0, randomCount);
            }
            questionDOList.forEach(questionDO -> {
                QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                questionSetItemDO.setQuestionSetId(requestParam.getQuestionSetId());
                questionSetItemDOList.add(questionSetItemDO);
                // 收集题目ID集合
                questionIdSet.add(questionDO.getQuestionId());
            });
        }
        Integer rowCount = batchSave();
        log.info("{}随机抽题责任链执行逻辑完成，计划-总题目数量：{},实际-总题目数量:{}",
                requestParam.getCategory(), randomCount, rowCount);
    }

    @Override
    public int getOrder() {
        return 30;
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
