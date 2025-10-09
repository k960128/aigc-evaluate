package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;


@TableName("question_tag_mapping")
@KeySequence("question_tag_mapping_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTagMappingDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 题目唯一编号
     */
    private String questionId;

    /**
     * 标签ID
     */
    private String tagId;

    public static QuestionTagMappingDO create(String questionId, String tagId) {
        return QuestionTagMappingDO.builder()
                .questionId(questionId)
                .tagId(tagId)
                .build();
    }
}
