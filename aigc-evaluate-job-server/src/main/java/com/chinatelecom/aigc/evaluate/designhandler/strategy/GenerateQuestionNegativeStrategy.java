package com.chinatelecom.aigc.evaluate.designhandler.strategy;

import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.common.enums.GenerateQuestionChainEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.designhandler.strategy.base.AbstractGenerateQuestionHandler;
import com.chinatelecom.aigc.evaluate.designpattern.chain.AbstractChainContext;
import com.chinatelecom.aigc.evaluate.designpattern.strategy.AbstractExecuteStrategy;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConf;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;


@Slf4j
@Service
public class GenerateQuestionNegativeStrategy extends AbstractGenerateQuestionHandler implements AbstractExecuteStrategy<QuestionSetInfoDO, Boolean> {

    private final AbstractChainContext<ExtractStep> generateQuestionAbstractChainContext;

    public GenerateQuestionNegativeStrategy(AbstractChainContext<ExtractStep> generateQuestionAbstractChainContext) {
        this.generateQuestionAbstractChainContext = generateQuestionAbstractChainContext;
    }

    @Override
    public Boolean generateQuestion(QuestionSetInfoDO questionSetInfoDO) {
        log.info("负向题库生成题集");
        // 1.1 获取生成规则
        ExtractConf extractConf = JSON.parseObject(questionSetInfoDO.getExtractConf(), ExtractConf.class);
        // 1.2 生成步骤器
        ExtractStep step = ExtractStep.builder()
                .questionSetId(questionSetInfoDO.getId())
                .category(mark())
                .difficultyConf(extractConf.getNegativeConf().getDifficultyConf())
                .randomConf(extractConf.getNegativeConf().getRandomConf())
                .customConf(extractConf.getNegativeConf().getCustomConf())
                .questionIdSet(new HashSet<>())
                .build();
        // 2.责任链生成题目
        generateQuestionAbstractChainContext.handler(GenerateQuestionChainEnum.GENERATE_QUESTION_FILTER.name(), step);
        return true;
    }

    @Override
    public String mark() {
        return QuestionCategoryEnum.NEGATIVE.name();
    }

    @Override
    public Boolean executeResp(QuestionSetInfoDO requestParam) {
        return generateQuestion(requestParam);
    }
}
