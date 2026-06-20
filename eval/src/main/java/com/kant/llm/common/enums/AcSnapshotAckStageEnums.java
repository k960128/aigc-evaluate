package com.kant.llm.common.enums;

import lombok.Getter;

/**
 * AC 快照节点确认阶段。
 */
@Getter
public enum AcSnapshotAckStageEnums {

    BUILD("BUILD"),

    ACTIVE("ACTIVE");

    private final String code;

    AcSnapshotAckStageEnums(String code) {
        this.code = code;
    }
}
