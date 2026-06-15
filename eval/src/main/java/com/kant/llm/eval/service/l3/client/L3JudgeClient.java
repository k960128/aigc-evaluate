package com.kant.llm.eval.service.l3.client;

import com.kant.llm.eval.service.l3.model.L3JudgeRequest;
import com.kant.llm.eval.service.l3.model.L3JudgeResult;

/**
 * L3 Judge 客户端接口。
 *
 * <p>真实大模型 Judge、规则降级 Judge 或测试桩都通过该接口接入 L3 主流程。</p>
 */
public interface L3JudgeClient {

    /**
     * 调用 L3 Judge。
     *
     * @param request Judge 请求
     * @return Judge 判定结果
     */
    L3JudgeResult judge(L3JudgeRequest request);
}
