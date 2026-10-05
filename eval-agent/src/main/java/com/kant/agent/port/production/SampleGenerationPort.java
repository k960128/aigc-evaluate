package com.kant.agent.port.production;

/** 样本生成模型端口，不引用ProbeExecutionPort，不具备调用被测目标或业务工具的能力。 */
public interface SampleGenerationPort {
    /** 只读生成角色配置，不调用模型；身份参数变化时更新profileRef，enabled仅控制可用性。 */
    Profile profile();

    /**
     * 对固定槽位生成或改写候选，持久化、预算与重试决策由外层工作流管理。
     * @param command 已固定角色、提示词与本轮资料的命令
     * @return 可确认的生成分类与用量；无法确认的请求由适配器抛出异常，不伪造零消耗
     */
    Result generate(Command command);

    /**
     * 单一模型角色的只读快照，由Actor和Critic端口分别返回，区别于计划组合身份和被测目标声明。
     * @param profileRef 当前角色配置指纹，角色名与提示词版本也参与摘要；不是计划的组合profileRef
     * @param available 是否显式启用且配置可用，不是模型健康检查结果
     * @param modelName 当前角色模型标签，两种角色可选择相同或不同模型，不是被测目标名称
     * @param maxOutputTokens 当前角色单次输出上限，用于该阶段调用前Token预留
     */
    record Profile(String profileRef, boolean available, String modelName, int maxOutputTokens) {
    }

    /**
     * 持久化的固定命令，含受限正文，不应出现在普通列表或日志中。
     * @param operationId 批次、根槽位、轮次及角色确定的操作身份；生成与评审分别使用genop/refop命名空间
     * @param profileRef 本次生成或评审绑定的单角色指纹，不可拿计划组合指纹代替
     * @param systemPrompt 平台固定指令，不能由种子或反馈覆盖
     * @param userPayloadJson 种子、变异、父候选、反馈或待评审正文等JSON资料，按不可信数据处理
     */
    record Command(String operationId, String profileRef, String systemPrompt, String userPayloadJson) {
    }

    /**
     * 已确认响应的分类和计量，不包含内部思维链，仍须质检和人工准入。
     * @param status GENERATED结构合法、REFUSED结构化拒绝、INVALID结构不合法
     * @param content 可空候选输入，拒绝或非法响应不能伪造可执行样本
     * @param reason 适配器固定分类说明，不照搬模型原始拒绝原因
     * @param inputTokens 本次输入Token，必须非负
     * @param outputTokens 本次输出Token，必须非负，非法结果也需计量
     * @param measurement ACTUAL为供应商实报，ESTIMATED为平台估算
     */
    record Result(String status, String content, String reason, long inputTokens, long outputTokens,
                  String measurement) {
    }
}
