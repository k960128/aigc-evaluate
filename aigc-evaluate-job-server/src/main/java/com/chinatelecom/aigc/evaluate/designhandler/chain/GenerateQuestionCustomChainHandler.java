package com.chinatelecom.aigc.evaluate.designhandler.chain;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractConfCustom;
import com.chinatelecom.aigc.evaluate.dto.model.ExtractStep;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义题目链
 */
@Slf4j
@Component
public class GenerateQuestionCustomChainHandler implements GenerateQuestionChainFilter<ExtractStep> {
    private final QuestionMapper questionMapper;
    private final QuestionSetItemMapper questionSetItemMapper;
    private List<QuestionSetItemDO> questionSetItemDOList;

    public GenerateQuestionCustomChainHandler(QuestionMapper questionMapper,
                                              QuestionSetItemMapper questionSetItemMapper) {
        this.questionMapper = questionMapper;
        this.questionSetItemMapper = questionSetItemMapper;
    }

    @Override
    public void handler(ExtractStep requestParam) {
        log.info("自定义选题责任链执行逻辑,题库：{},题解ID：{}", requestParam.getCategory(), requestParam.getQuestionSetId());
        ExtractConfCustom customConf = requestParam.getCustomConf();
        if (ObjectUtil.isNull(customConf)) {
            return;
        }
        if (CollectionUtil.isNotEmpty(customConf.getQuestionIdList())) {
            // 初始化保存结果集合对象
            questionSetItemDOList = new ArrayList<>(customConf.getQuestionIdList().size());
            questionMapper.selectList(customConf.getQuestionIdList()).forEach(questionDO -> {
                QuestionSetItemDO questionSetItemDO = new QuestionSetItemDO();
                questionSetItemDO.setQuestionId(questionDO.getQuestionId());
                questionSetItemDO.setQuestionVersion(questionDO.getVersion());
                questionSetItemDO.setQuestionSetId(requestParam.getQuestionSetId());
                questionSetItemDOList.add(questionSetItemDO);
                // 收集题目ID集合
                requestParam.getQuestionIdSet().add(questionDO.getQuestionId());
            });
            // 执行批处理操作
            Integer rowCount = batchSave();
            log.info("{}自定义选题责任链执行逻辑完成，计划-总题目数量：{},实际-总题目数量:{}",
                    requestParam.getCategory(), customConf.getQuestionIdList().size(), rowCount);
        }
    }

    @Override
    public int getOrder() {
        return 10;
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
