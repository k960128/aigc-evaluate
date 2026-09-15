package com.kant.agent.gateway;

import com.kant.agent.contract.TargetModelRequest;
import com.kant.agent.contract.TargetModelResponse;

/**
 * 目标模型业务网关，隔离编排器与具体模型厂商及 Spring AI 实现。
 *
 * <p>网关负责连接目标模型、转换基础设施层对象并统一处理调用错误。上层业务不应
 * 直接依赖具体厂商客户端或 Spring AI 的请求响应类型。</p>
 */
public interface TargetModelGateway {

    /**
     * 调用目标模型并返回统一的调用事实。
     *
     * @param request 目标模型调用请求
     * @return 成功或失败的统一响应；实现不应向编排层泄漏底层 SDK 类型
     */
    TargetModelResponse call(TargetModelRequest request);
}
