package com.chinatelecom.aigc.evaluate.designhandler.chain;

import com.chinatelecom.aigc.evaluate.common.enums.GenerateQuestionChainEnum;
import com.chinatelecom.aigc.evaluate.designpattern.chain.AbstractChainHandler;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractStep;

public interface GenerateQuestionChainFilter<T extends ExtractStep> extends AbstractChainHandler<ExtractStep> {
    @Override
    default String mark() {
        return GenerateQuestionChainEnum.GENERATE_QUESTION_FILTER.name();
    }

    Integer batchSave();
}
