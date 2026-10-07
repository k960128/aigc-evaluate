package com.kant.llm.eval.agent.production.error;

/**
 * D05批次执行原因的中文展示文案，由后端统一维护，前端不需要翻译错误编码。
 * <p>数据库仍保存原errorCode，不改写历史事实，也不影响预算、租约和状态流转。
 * 只返回平台固定文案，不拼接供应商异常、SQL、提示词或候选正文。</p>
 */
public final class SampleRunErrorMessages {
    /** 仅提供静态文案转换，不持有运行状态，不访问数据库或模型。 */
    private SampleRunErrorMessages() {
    }

    /**
     * 将已保存的批次执行原因转换为可直接展示的中文说明。
     * <p>旧记录没有保存预算不足的具体维度和下一次预留数值，因此不推测精确金额或Token明细。
     * 未识别的非空编码仍提供通用提示，原errorCode继续返回，方便定位。</p>
     * @param errorCode 批次错误、停止或阻塞编码，不是接口外壳的code
     * @return 无原因时为null，其余为安全中文文案；前端按纯文本展示
     */
    public static String messageFor(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) return null;
        return switch (errorCode) {
            case "PRODUCTION_BUDGET_EXHAUSTED" ->
                    "生产预算不足，无法继续调用模型。请检查调用次数上限，以及剩余Token是否足以覆盖下一次调用预留；已有候选和审批结果保留。";
            case "TOKEN_BUDGET_EXCEEDED" ->
                    "已确认的模型Token用量超过批次预算，已停止后续调用；实际消耗和已有候选保留。";
            case "DEADLINE_EXPIRED" ->
                    "生产批次已超过执行期限（包含排队时间），已停止后续调用；已有候选保留。";
            case "GENERATOR_CONFIGURATION_CHANGED" ->
                    "生成或评审角色不可用，或当前模型配置与计划快照不一致；请检查角色配置，并按需重置或新建生产计划。";
            case "REFLECTION_UPGRADE_REQUIRED" ->
                    "该批次使用旧版执行配置，不能继续新增模型调用；请重置或新建生产计划后启动新批次。";
            case "GENERATION_RESULT_UNKNOWN" ->
                    "模型调用结果尚未确认，批次已阻塞；系统保留预算预留，不会自动重发请求，请核查原调用结果。";
            case "PREPARATION_INTERRUPTED" ->
                    "模型调用准备过程被中断，本次尚未发送的请求已取消；已有结果保留。";
            case "STOP_DEADLINE_OR_CONFIGURATION" ->
                    "调用前检查未通过：可能已请求停止、执行期限已到或模型角色配置发生变化；本次尚未发送的请求已取消。";
            case "CALL_BOUNDARY_REJECTED" ->
                    "调用前执行条件校验未通过，本次尚未发送的请求已取消；请核查执行租约、批次状态、期限及模型角色配置。";
            case "WORKFLOW_INTERRUPTED" ->
                    "样本生产推进发生异常，批次已停止；请联系管理员根据批次ID核查后台日志，已有结果保留。";
            case "STOP_REQUESTED" ->
                    "已按请求停止新增模型调用，已有候选和已确认用量保留。";
            default -> "生产批次出现未识别的执行原因，请联系管理员根据批次ID和错误编码核查。";
        };
    }
}
