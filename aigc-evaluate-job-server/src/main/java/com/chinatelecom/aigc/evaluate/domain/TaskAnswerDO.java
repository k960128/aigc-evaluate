package com.chinatelecom.aigc.evaluate.domain;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@TableName("task_answer")
@KeySequence("task_answer_seq") // 适用于 Oracle、PostgreSQL 等数据库，MySQL 可省略
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAnswerDO extends BaseDO {
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型编号
     */
    private Long modelId;

    /**
     * 题目唯一编号
     */
    private String questionId;

    /**
     * 题目版本号
     */
    private Integer questionVersion;
    /**
     * 题目内容
     */
    private String questionContent;
    /**
     * 题目答案
     */
    private String answerContent;
    /**
     * 所属题库
     */
    private String questionCategory;
    /**
     * 人工审核结果
     */
    private Integer judgeResult;
    /**
     * 设置自动判定结果
     */
    private Integer violation;
    /**
     * 推理过程
     */
    private String thinkProcess;
    /**
     * 模型名字
     */
    private String modelName;
    /**
     * 模型名字
     */
    private String modelVersion;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 习题集
     */
    private Long questionSet;

    /**
     * 任务ID
     */
    private Long taskId;


}

