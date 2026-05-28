package com.kant.llm.rag;

import com.kant.llm.eval.client.ModelClientStrategyFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG优化-重写
 */
@RestController
@RequestMapping("/rag/rewrite")
public class RagQuestionRewriteController {

    private final ModelClientStrategyFactory modelClientStrategyFactory;

    public RagQuestionRewriteController(ModelClientStrategyFactory modelClientStrategyFactory) {
        this.modelClientStrategyFactory = modelClientStrategyFactory;
    }

    /**
     * 分解
     */
    private String DECOMPOSE_PORMPT = "# 角色\n" +
            "你是一名专业的查询逻辑分析专家\n" +
            "\n" +
            "# 任务\n" +
            "将给定的 ``用户原始问题``分解为一系列**相互独立、逻辑清晰**，且可能单独用于检索的子查询列表\n" +
            "\n" +
            "# 严格遵循\n" +
            "1. 你的输出必须是一个标准的JSON数组格式。\n" +
            "2. 不强制要求数组元素格式，可根据真实情况输出，至少保留一个。\n" +
            "\n" +
            "# 用户原始问题\n" +
            "{QUESTION}\n" +
            "\n" +
            "# 输出格式（JSON Array）\n" +
            "[\n" +
            "  \"子查询1\",\n" +
            "  \"子查询2\",\n" +
            "  \"子查询3\"\n" +
            "]";

}
