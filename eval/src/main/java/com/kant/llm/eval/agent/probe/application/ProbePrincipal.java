package com.kant.llm.eval.agent.probe.application;

/**
 * 服务端认证后得到的探测调用身份。
 *
 * @param tenantId 当前租户或业务空间标识
 * @param actorId  当前登录用户标识
 */
public record ProbePrincipal(String tenantId, String actorId) {
}
