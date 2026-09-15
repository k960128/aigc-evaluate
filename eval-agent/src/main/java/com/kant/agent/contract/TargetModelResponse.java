package com.kant.agent.contract;

/**
 * 目标模型调用事实。
 *
 * <p>无论底层调用成功还是失败，网关都将结果转换成该对象，编排器因此可以用统一
 * 的方式记录耗时、错误类型和目标模型响应。</p>
 *
 * @param model 实际调用的模型名称
 * @param content 目标模型返回的文本内容；调用失败时为空
 * @param status 调用成功或失败状态
 * @param errorType 失败类型；成功时为 NONE
 * @param elapsedMillis 本次调用耗时，单位为毫秒
 * @param retryCount 已执行的重试次数，当前 M1 固定为 0
 * @param errorMessage 面向排障的错误信息；成功时为空
 */
public record TargetModelResponse(
        String model,
        String content,
        ModelCallStatus status,
        ModelCallErrorType errorType,
        long elapsedMillis,
        int retryCount,
        String errorMessage
) {
    /**
     * 创建一次成功的目标模型调用结果。
     *
     * @param model 实际调用的模型名称
     * @param content 模型返回内容
     * @param elapsedMillis 调用耗时，单位为毫秒
     * @return 表示调用成功的响应事实
     */
    public static TargetModelResponse success(String model, String content, long elapsedMillis) {
        return new TargetModelResponse(model, content, ModelCallStatus.SUCCESS,
                ModelCallErrorType.NONE, elapsedMillis, 0, null);
    }

    /**
     * 创建一次失败的目标模型调用结果。
     *
     * @param model 实际调用的模型名称
     * @param errorType 统一后的失败类型
     * @param elapsedMillis 调用耗时，单位为毫秒
     * @param errorMessage 错误描述
     * @return 表示调用失败的响应事实
     */
    public static TargetModelResponse failed(String model, ModelCallErrorType errorType,
                                              long elapsedMillis, String errorMessage) {
        return new TargetModelResponse(model, null, ModelCallStatus.FAILED,
                errorType, elapsedMillis, 0, errorMessage);
    }
}
