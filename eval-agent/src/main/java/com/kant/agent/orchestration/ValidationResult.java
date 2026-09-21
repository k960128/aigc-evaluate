package com.kant.agent.orchestration;

import java.util.List;
import java.util.stream.Collectors;

import static com.kant.agent.contract.common.ContractChecks.required;

/**
 * 业务校验结果。
 *
 * @param issues 校验问题集合；为空表示通过
 */
public record ValidationResult(List<ValidationIssue> issues) {

    public ValidationResult {
        required(issues, "issues");
        issues = List.copyOf(issues);
    }

    public boolean passed() {
        return issues.isEmpty();
    }

    public static ValidationResult success() {
        return new ValidationResult(List.of());
    }

    public static ValidationResult failure(
            String field,
            String code,
            String message
    ) {
        return new ValidationResult(
                List.of(new ValidationIssue(field, code, message))
        );
    }

    /**
     * 校验不通过时阻止调用方继续处理。
     *
     * <p>M1 可以在应用层将该异常转换成项目统一业务异常。</p>
     */
    public void throwIfInvalid() {
        if (!passed()) {
            String message = issues.stream()
                    .map(issue -> issue.code() + "：" + issue.message())
                    .collect(Collectors.joining("；"));
            throw new IllegalArgumentException(message);
        }
    }
}