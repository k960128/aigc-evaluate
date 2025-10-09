package com.chinatelecom.aigc.evaluate.domain;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chinatelecom.aigc.evaluate.common.util.snow.CodeUtils;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionTagUpdateReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

@TableName("question_tag_info")
@KeySequence("question_info_tag_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTagInfoDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标签ID
     */
    private String tagId;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 标签描述
     */
    private String tagDesc;

    /**
     * 标签级别
     */
    private Integer tagLevel;

    /**
     * 父级标签ID
     */
    private String parentId;

    /**
     * 标签类型
     */
    private String type;

    /**
     * 标签所属题库
     */
    private String category;

    public static QuestionTagInfoDO create(QuestionTagSaveReq req, QuestionTagInfoDO parent) {
        return QuestionTagInfoDO.builder()
                .tagId(String.valueOf(CodeUtils.getSnowFlakeId()))
                .tagName(req.getTagName())
                .tagDesc(req.getTagDesc())
                .tagLevel(ObjectUtil.isNull(parent) ? 1 : parent.getTagLevel() + 1)
                .parentId(req.getParentId())
                .type("")
                .category(req.getCategory())
                .build();
    }

    public static QuestionTagInfoDO update(QuestionTagUpdateReq req, QuestionTagInfoDO parent) {
        return QuestionTagInfoDO.builder()
                .id(req.getId())
                .tagId(req.getTagId())
                .tagName(req.getTagName())
                .tagDesc(req.getTagDesc())
                .tagLevel(ObjectUtil.isNull(parent) ? 1 : parent.getTagLevel() + 1)
                .parentId(req.getParentId())
                .type("")
                .category(req.getCategory())
                .build();
    }
}
