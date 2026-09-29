package com.kant.agent.contract.common;

import java.util.Collection;

/**
 * 智能体领域契约的通用参数校验工具。
 *
 * <p>该工具只依赖 JDK，确保 {@code eval-agent} 中的业务契约不会与
 * Spring、Spring AI 或具体持久化框架耦合。</p>
 */
public final class ContractChecks {

    private ContractChecks() {
    }

    /**
     * 校验对象不能为空。
     *
     * @param value     待校验对象
     * @param fieldName 字段名称
     * @param <T>       对象类型
     * @return 原对象
     */
    public static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    /**
     * 校验字符串不能为空白并去除首尾空格。
     *
     * @param value     待校验字符串
     * @param fieldName 字段名称
     * @return 去除首尾空格后的字符串
     */
    public static String notBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    /**
     * 校验字符串最大长度。
     *
     * @param value     待校验字符串
     * @param maxLength 最大允许长度
     * @param fieldName 字段名称
     * @return 原字符串
     */
    public static String maxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength);
        }
        return value;
    }

    /**
     * 将可空字符串规范化为去除首尾空格后的值。
     *
     * @param value 原始字符串
     * @return 规范化字符串；空白字符串返回 {@code null}
     */
    public static String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验数值不能为负数。
     *
     * @param value     待校验数值
     * @param fieldName 字段名称
     * @return 原数值
     */
    public static long nonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
        return value;
    }

    /**
     * 校验数值必须为正数。
     *
     * @param value     待校验数值
     * @param fieldName 字段名称
     * @return 原数值
     */
    public static long positive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
        return value;
    }

    /**
     * 校验集合至少包含一项。
     *
     * @param value     待校验集合
     * @param fieldName 字段名称
     * @param <T>       集合类型
     * @return 原集合
     */
    public static <T extends Collection<?>> T notEmpty(T value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    /**
     * 校验业务条件。
     *
     * @param condition 条件是否成立
     * @param message   条件不成立时的提示
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
