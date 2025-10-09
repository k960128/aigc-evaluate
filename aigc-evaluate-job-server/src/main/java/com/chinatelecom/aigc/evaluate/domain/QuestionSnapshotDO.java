package com.chinatelecom.aigc.evaluate.domain;

import cn.hutool.crypto.digest.MD5;
import com.baomidou.mybatisplus.annotation.*;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionUpdateReq;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

@TableName("question_snapshot")
@KeySequence("question_snapshot_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSnapshotDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题目唯一编号
     */
    private String questionId;

    /**
     * 题目内容
     */
    private String title;

    /**
     * 所属题库
     */
    private String category;

    /**
     * 标签
     */
    private String tags;

    /**
     * 题目内容MD5哈希值
     */
    private String contentHash;

    /**
     * 题目难度
     */
    private String difficulty;

    /**
     * 攻击方式
     */
    private String attackMethod;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * 题目版本号
     */
    private Integer version;

    /**
     * 描述(备注)
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 创建题目快照
     * 充血模型
     *
     * @param req
     * @return
     */
    public static QuestionSnapshotDO create(QuestionDO req) {
        return QuestionSnapshotDO.builder()
                .questionId(req.getQuestionId())
                .title(req.getTitle())
                .category(req.getCategory())
                .tags(req.getTags())
                .difficulty(req.getDifficulty())
                .attackMethod(req.getAttackMethod())
                .contentHash(req.getContentHash())
                .version(req.getVersion())
                .dataSource(req.getDataSource())
                .desc(req.getDesc())
                .build();
    }
}
