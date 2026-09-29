package com.kant.llm.eval.agent.probe.application;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 使用项目现有 Sa-Token 登录态提供探测身份。
 *
 * <p>当前工程尚未提供明确租户来源，因此 D01 将业务空间封装为可配置默认值。
 * 请求体不能覆盖租户或操作者；接入真实租户系统时只需替换本实现。</p>
 */
@Component
public class SaTokenProbePrincipalProvider implements ProbePrincipalProvider {

    private final String defaultTenantId;

    public SaTokenProbePrincipalProvider(
            @Value("${agent.probe.default-tenant-id:default}") String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public ProbePrincipal currentPrincipal() {
        return new ProbePrincipal(defaultTenantId, StpUtil.getLoginIdAsString());
    }
}
