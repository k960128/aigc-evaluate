package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

import java.util.List;
import java.util.Map;

@TableName("question_set_info")
@KeySequence("question_set_info_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSetInfoDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String questionSetName;

    /**
     * 所选题库
     */
    private String questionCategory;

    /**
     * 评测目标
     */
    private String evaluationTarget;

    /**
     * 评测描述
     */
    private String description;
    /**
     * 抽取题目维度配置
     */
    private String extractConf;

    /**
     * 是否生成题集
     */
    private Boolean generate;

    @TableField(exist = false)
    private Map<String, List<QuestionSetItemResp>> questionSetItemMap;
}
