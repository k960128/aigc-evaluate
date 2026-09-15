package com.kant.agent.contract;

/**
 * 目标模型网关请求，不暴露 Spring AI 类型。
 *
 * <p>编排层只依赖这个稳定的业务请求对象，具体的 Spring AI ChatClient、
 * 厂商 SDK 或 Mock 实现由 gateway 层负责转换。</p>
 *
 * @param taskId 所属评测任务标识
 * @param sampleId 本次调用使用的攻击样本标识
 * @param model 目标模型名称
 * @param manufacturer 目标模型厂商名称
 * @param baseUrl 目标模型服务地址
 * @param apiKey 目标模型访问凭证
 * @param modelId 目标模型数值标识
 * @param input 发送给目标模型的攻击样本内容
 * @param mockResponse 测试阶段的固定响应；为空时由真实网关调用目标模型
 */
public record TargetModelRequest(
        String taskId,
        String sampleId,
        String model,
        String manufacturer,
        String baseUrl,
        String apiKey,
        long modelId,
        String input,
        String mockResponse
) {
}
