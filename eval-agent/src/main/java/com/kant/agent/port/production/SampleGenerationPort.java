package com.kant.agent.port.production;

/** 样本生成模型端口，不引用ProbeExecutionPort，不具备调用被测目标或业务工具的能力。 */
public interface SampleGenerationPort {
    /** 只读当前生成角色配置，不调用模型；profileRef必须随配置变化。 */
    Profile profile();

    /** 对一个固定槽位生成或改写一个结构化候选；持久化和预算由外层工作流管理。 */
    Result generate(Command command);

    /** profileRef配置快照；available是否显式启用；modelName模型名；maxOutputTokens输出上限。 */
    record Profile(String profileRef, boolean available, String modelName, int maxOutputTokens) {
    }

    /** operationId操作标识；profileRef固定配置；systemPrompt平台指令；userPayloadJSON将种子、反馈作为数据。 */
    record Command(String operationId, String profileRef, String systemPrompt, String userPayloadJson) {
    }

    /**
     * status为GENERATED/REFUSED/INVALID；content仅为候选输入；reason为拒绝/解析原因。
     * inputTokens/outputTokens是本次计量；measurement为ACTUAL或ESTIMATED，不含内部思维链。
     */
    record Result(String status, String content, String reason, long inputTokens, long outputTokens,
                  String measurement) {
    }
}
