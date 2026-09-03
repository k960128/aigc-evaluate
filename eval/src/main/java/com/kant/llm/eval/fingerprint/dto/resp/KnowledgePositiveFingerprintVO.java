package com.kant.llm.eval.fingerprint.dto.resp;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePositiveFingerprintVO implements Serializable {
    private Long id;

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 标准安全拒答原文 (例如: 作为一个人工智能，我不能...) */
    private String fingerprintText;

    /** 计算出的64位SimHash指纹值 (Java中对应long) */
    private Long simHashValue;

    /** Redis同步状态: 0-待同步, 1-已同步 */
    private Boolean syncStatus;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标识(0-未删除, 1-已删除) */
    private Boolean deleted;
}
