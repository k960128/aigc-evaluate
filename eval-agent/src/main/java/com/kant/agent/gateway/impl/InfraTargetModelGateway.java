package com.kant.agent.gateway.impl;

import com.kant.agent.contract.ModelCallErrorType;
import com.kant.agent.contract.TargetModelRequest;
import com.kant.agent.contract.TargetModelResponse;
import com.kant.agent.gateway.TargetModelGateway;
import com.kant.ai.infra.client.ClientModelInfo;
import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import org.springframework.stereotype.Service;

/**
 * 基于 infra-ai 的目标模型网关实现。
 *
 * <p>该类是 eval-agent 与现有 infra-ai 模块之间的适配层：先把评测领域请求转换成
 * 基础设施层的模型信息和调用请求，再把基础设施层响应转换回领域响应。</p>
 */
@Service
public class InfraTargetModelGateway implements TargetModelGateway {

    private final ModelClientStrategyFactory strategyFactory;

    /**
     * 创建目标模型网关。
     *
     * @param strategyFactory 根据厂商选择模型客户端策略的工厂
     */
    public InfraTargetModelGateway(ModelClientStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    /**
     * 调用目标模型，并将底层异常统一转换为领域错误类型。
     *
     * @param request 评测编排器传入的目标模型请求
     * @return 成功或失败的目标模型调用事实
     */
    @Override
    public TargetModelResponse call(TargetModelRequest request) {
        // 从入口开始计时，保证成功和失败结果都能返回调用耗时。
        long start = System.currentTimeMillis();
        try {
            // 领域层只保存字符串厂商名称，这里才转换为 infra-ai 的枚举类型。
            ModelManufacturerEnum manufacturer = ModelManufacturerEnum.valueOf(request.manufacturer());

            // 将领域请求组装为基础设施层客户端信息，避免上层依赖其具体类型。
            ClientModelInfo modelInfo = ClientModelInfo.builder()
                    .modelId(request.modelId())
                    .model(request.model())
                    .baseUrl(request.baseUrl())
                    .apiKey(request.apiKey())
                    .manufacturerType(manufacturer)
                    .build();

            // 工厂根据厂商选择实际客户端；MOCK 也沿用统一的策略选择入口。
            ModelClientStrategy strategy = strategyFactory.getStrategy(modelInfo);
            ModelResponse response = strategy.call(ModelRequest.builder()
                    .clientModelInfo(modelInfo)
                    .inputText(request.input())
                    .mockModelOutput(request.mockResponse())
                    .build());

            if (response == null || response.getRespContent() == null) {
                // 调用没有抛异常但响应不完整，同样视为网关失败，避免 Judge 分析空事实。
                return TargetModelResponse.failed(request.model(), ModelCallErrorType.INVALID_RESPONSE,
                        elapsed(start), "目标模型返回为空");
            }
            // 只向上层返回统一的文本响应和耗时，不泄漏基础设施层响应对象。
            return TargetModelResponse.success(request.model(), response.getRespContent(), elapsed(start));
        } catch (IllegalArgumentException e) {
            // valueOf 失败或基础设施层参数校验失败，归类为请求配置问题。
            return TargetModelResponse.failed(request.model(), ModelCallErrorType.INVALID_REQUEST,
                    elapsed(start), "目标模型厂商配置无效：" + request.manufacturer());
        } catch (Exception e) {
            // M1 保留统一兜底错误类型；后续可按厂商异常细分鉴权、超时和限流错误。
            return TargetModelResponse.failed(request.model(), ModelCallErrorType.UNKNOWN,
                    elapsed(start), e.getMessage());
        }
    }

    /**
     * 计算从调用开始到当前时刻经过的毫秒数。
     *
     * @param start 调用开始时的时间戳
     * @return 已耗时毫秒数
     */
    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
