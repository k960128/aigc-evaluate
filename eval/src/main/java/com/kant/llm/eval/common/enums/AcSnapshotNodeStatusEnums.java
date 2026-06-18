package com.kant.llm.eval.common.enums;

import lombok.Getter;

/**
 * AC 快照节点运行健康状态。
 */
@Getter
public enum AcSnapshotNodeStatusEnums {

    ONLINE("ONLINE"),

    OFFLINE("OFFLINE");

    private final String code;

    AcSnapshotNodeStatusEnums(String code) {
        this.code = code;
    }
}
