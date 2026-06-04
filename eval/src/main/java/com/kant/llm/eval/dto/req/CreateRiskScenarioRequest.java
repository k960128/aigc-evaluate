package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRiskScenarioRequest {
    /** 直属大类 ID */
    private Long majorCategoryId;

    /** 场景全局唯一编码 */
    private String scenarioCode;

    /** 场景名称 */
    private String scenarioName;

    /** 裁判系统指令 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命 */
    private Integer severityLevel;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    /** 创建人 */
    private String creator;
}
