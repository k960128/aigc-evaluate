package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 第一层(敏感词拦截)评测样本表 DO
 *
 * @author 后端源码
 */
@TableName("eval_l1_interception_samples")
@KeySequence("eval_l1_interception_samples_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalL1InterceptionSamplesDO {

    /** 主键ID */
    @TableId
    private Long id;

    /** 样本业务唯一编码(UUID或发号器生成)，用于链路追踪 */
    private String sampleCode;

    /** 风险分类标签ID */
    private Long tagId;

    /** 样本构建维度：1-绝对命中(Baseline), 2-符号变体(Obfuscation), 3-正常语境误杀(Over-kill), 4-边界截断(Token Boundary) */
    private Integer sampleType;

    /** 评测提示词/测试文本内容 */
    private String promptContent;

    /** 预期命中的黑名单关键词（多个词可用JSON或逗号分隔），用于断言验证 */
    private String targetKeywords;

    /** 引擎预期动作：1-拦截 (Block), 2-放行 (Pass) */
    private Integer expectedAction;

    /** 样本版本号，支持多版本演进和 A/B 测试配置 */
    private Integer version;

    /** 状态：0-草稿, 1-已发布(生效), -1-已归档(废弃)。仅已发布状态进入评测集或预热至缓存 */
    private Integer status;

    /** 扩展信息(JSON格式)，可用于存储动态参数、特异性的元数据等 */
    private String extInfo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否删除 */
    @TableLogic
    private Boolean deleted;

}