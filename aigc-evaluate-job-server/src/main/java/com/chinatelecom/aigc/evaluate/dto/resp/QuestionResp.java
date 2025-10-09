package com.chinatelecom.aigc.evaluate.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@ApiModel(value = "管理后台 - 题目 Resp VO")
public class QuestionResp {

    @ApiModelProperty(value = "主键")
    private Long id;
    /**
     * 题目唯一编号
     */
    @ApiModelProperty(value = "题目唯一编号")
    private String questionId;

    /**
     * 题目内容
     */
    @ApiModelProperty(value = "题目内容")
    private String title;

    /**
     * 所属题库
     */
    @ApiModelProperty(value = "所属题库")
    private String category;

    /**
     * 标签
     */
    @ApiModelProperty(value = "标签")
    private String tags;

    /**
     * 题目内容MD5哈希值
     */
    @ApiModelProperty(value = "题目内容MD5哈希值")
    private String contentHash;

    /**
     * 题目难度
     */
    @ApiModelProperty(value = "题目难度")
    private String difficulty;

    /**
     * 攻击方式
     */
    @ApiModelProperty(value = "攻击方式")
    private String attackMethod;

    /**
     * 数据来源
     */
    @ApiModelProperty(value = "数据来源")
    private String dataSource;

    @ApiModelProperty(value = "题目描述")
    private String desc;

    @ApiModelProperty(value = "创建时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
