package com.kant.llm.common.enums;

import lombok.Getter;

/**
 * AC 快照节点确认状态。
 */
@Getter
public enum AcSnapshotAckStatusEnums {

    SUCCESS("SUCCESS"),

    FAILED("FAILED");

    private final String code;

    AcSnapshotAckStatusEnums(String code) {
        this.code = code;
    }
}
