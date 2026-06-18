package com.kant.llm.eval.common.enums;

import lombok.Getter;

/**
 * 单次 AC 快照发布记录的生命周期状态。
 */
@Getter
public enum AcSnapshotPublishStatusEnums {

    SNAPSHOT_READY("SNAPSHOT_READY"),

    BUILDING("BUILDING"),

    LATEST_SWITCHED("LATEST_SWITCHED"),

    ACTIVE("ACTIVE"),

    FAILED("FAILED");

    private final String code;

    AcSnapshotPublishStatusEnums(String code) {
        this.code = code;
    }
}
