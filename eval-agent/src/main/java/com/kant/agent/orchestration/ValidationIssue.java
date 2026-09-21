package com.kant.agent.orchestration;

import static com.kant.agent.contract.common.ContractChecks.text;

/**
 * 一条业务校验问题。
 *
 * @param field   出问题的字段或业务位置
 * @param code    稳定的错误码，供程序区分错误类型
 * @param message 中文说明，可用于展示或生成修订反馈
 */
public record ValidationIssue(
        String field,
        String code,
        String message
) {

    public ValidationIssue {
        field = text(field, "field");
        code = text(code, "code");
        message = text(message, "message");
    }
}