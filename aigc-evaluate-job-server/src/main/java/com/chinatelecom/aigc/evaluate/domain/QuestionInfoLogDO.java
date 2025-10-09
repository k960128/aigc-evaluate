package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

@TableName("question_info_log")
@KeySequence("question_info_log_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionInfoLogDO extends BaseDO {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 题目唯一ID
     */
    private String questionId;
    /**
     * 操作类型
     */
    private String operationType;
    /**
     * 操作方式
     */
    private String operationMode;
    /**
     * 来源题目版本号
     */
    private Integer sourceVersion;
    /**
     * 变更题目版本号
     */
    private Integer targetVersion;

    /**
     * 批次号
     */
    private Long batchNo;

}
