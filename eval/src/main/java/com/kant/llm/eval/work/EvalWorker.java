package com.kant.llm.eval.work;

import com.kant.llm.eval.client.ModelInfo;
import com.kant.llm.eval.common.splitter.OverlapParagraphTextSplitter;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.service.AcAutomatonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 评测工作Worker
 */
@Slf4j
@Component
public class EvalWorker {

    private final AcAutomatonService acAutomatonService;

    @Lazy
    public EvalWorker(AcAutomatonService acAutomatonService) {
        this.acAutomatonService = acAutomatonService;
    }

    public void execute(EvalResultDetailDO evalResultDetailDO,
                        EvalTaskDetailDO evalTaskDetailDO,
                        ModelInfo modelInfo) {
        log.info("开始执行评测,任务ID:{}", evalTaskDetailDO.getTaskId());
        // 1. 拆分提示词
        OverlapParagraphTextSplitter overlapParagraphTextSplitter = new OverlapParagraphTextSplitter(500, 50);
        List<String> chunks = overlapParagraphTextSplitter.splitText(evalResultDetailDO.getModelOutput());
        // 2. 三层漏斗架构处理
        // 2.1 第一层 -> 风险词汇匹配
        chunks.forEach(chunk -> {
            String match = match(chunk);
        });
        // 2.2 第二层 -> 双路召回
        chunks.forEach(chunk -> {
            String match = hybrid(chunk);
        });
        // 2.3 第三层 -> LLM-as-judge
        chunks.forEach(chunk -> {
            String match = llmAsJudge(chunk);
        });
        // 3. 保存评测结果
        // 4. 更新任务状态
        // 5. 更新任务执行时间
    }


    private String match(String text) {
        return acAutomatonService.match(text);
    }

    private String hybrid(String text) {
        return "";
    }

    private String llmAsJudge(String text){
        return "";
    }

}
