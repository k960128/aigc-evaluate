package com.kant.agent.contract.approval;

/**
 * 人工对评测计划作出的决定。
 */
public enum ApprovalDecision {

    /** 批准当前版本。 */
    APPROVE,

    /** 退回修改，修改后需要重新审批。 */
    RETURN_FOR_REVISION,

    /** 拒绝计划，本次活动结束。 */
    REJECT
}