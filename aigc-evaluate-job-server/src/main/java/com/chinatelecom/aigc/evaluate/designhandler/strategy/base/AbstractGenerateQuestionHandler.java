package com.chinatelecom.aigc.evaluate.designhandler.strategy.base;

import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;

/**
 * 抽象生成题目组件
 */
public abstract class AbstractGenerateQuestionHandler {

    /**
     * 生成题目抽象接口
     * @return boolean
     */
    public abstract Boolean generateQuestion(QuestionSetInfoDO questionSetInfoDO);
}
