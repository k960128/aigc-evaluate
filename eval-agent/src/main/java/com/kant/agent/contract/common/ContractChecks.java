package com.kant.agent.contract.common;

/**
 * 契约对象的基础字段检查工具。
 *
 * <p>只检查字段本身是否合法，不查询数据库，不执行权限判断。</p>
 */
public final class ContractChecks {

    private ContractChecks() {
    }

    /**
     * 检查必填对象。
     */
    public static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    /**
     * 检查必填文本，并移除首尾空白。
     */
    public static String text(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.strip();
    }

    /**
     * 检查正整数。
     */
    public static void positive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }

    /**
     * 检查已有业务资源的 ID。
     */
    public static void positiveId(Long value, String fieldName) {
        required(value, fieldName);
        positive(value, fieldName);
    }

    /**
     * 检查非负数，例如 Token 消耗和样本数量。
     */
    public static void nonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " 不能小于 0");
        }
    }
}