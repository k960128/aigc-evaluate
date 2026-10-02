package com.kant.llm.eval.agent.probe.application;

/**
 * 从服务端认证上下文读取探测调用身份的接口。
 */
public interface ProbePrincipalProvider {

    /**
     * 获取当前已认证身份。
     *
     * @return 当前租户与操作者
     */
    ProbePrincipal currentPrincipal();
}
