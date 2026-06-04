package com.kant.llm.eval.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularyGroupVO {
    /** 主键 ID */
    private Long id;

    /** 分组名称 */
    private String name;

    /** 分组描述 */
    private String description;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
