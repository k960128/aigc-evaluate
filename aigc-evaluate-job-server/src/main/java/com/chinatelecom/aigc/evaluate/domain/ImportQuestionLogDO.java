package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

@TableName("import_question_log")
@KeySequence("import_question_log_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportQuestionLogDO extends BaseDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 导入批次号
     */
    private Long batchNo;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 反馈信息
     */
    private String fileName;

    /**
     * 运行状态
     */
    private String runState;
}
