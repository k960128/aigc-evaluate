package com.chinatelecom.aigc.evaluate.dto.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测结果 DO
 *
 * @author 后端源码
 */
@Data
@NoArgsConstructor
@ApiModel(value = "管理后台 - 评测结果")
public class EvaluateResultStatisticsResp {
    //正向题库
    @ApiModelProperty(value = "正向题库-未评判", example = "10")
    private Integer unjudgedForward;

    @ApiModelProperty(value = "正向题库-无法评判", example = "2")
    private Integer uncertainForward;

    @ApiModelProperty(value = "正向题库-正常回答", example = "20")
    private Integer answered;

    @ApiModelProperty(value = "正向题库-拒绝回答", example = "8")
    private Integer unanswered;

    @ApiModelProperty(value = "正向题库-生成违规内容", example = "8")
    private Integer generatedInappropriateContent;

    @ApiModelProperty(value = "正向题库-总数", example = "30")
    private Integer totalForward;

    //负向题库
    @ApiModelProperty(value = "负向题库-未评判", example = "5")
    private Integer unjudgedNegative;

    @ApiModelProperty(value = "负向题库-无法评判数量", example = "1")
    private Integer uncertainNegative;

    @ApiModelProperty(value = "负向题库-生成违规内容", example = "3")
    private Integer notRejected ;

    @ApiModelProperty(value = "负向题库-拒绝回答", example = "25")
    private Integer rejected;

    @ApiModelProperty(value = "负向题库-正向引导", example = "25")
    private Integer positiveGuidance;

    @ApiModelProperty(value = "负向题库-总数", example = "20")
    private Integer totalNegative;


    //最终结果
    @ApiModelProperty(value = "总数", example = "50")
    private Integer total;

    @ApiModelProperty(value = "正向题库-非拒答比例", example = "0.85")
    private Double nonRejected;

    @ApiModelProperty(value = "负向题库-应拒答比例", example = "0.15")
    private Double shouldReject;

    public EvaluateResultStatisticsResp(
            Integer unjudgedForward, Integer unjudgedNegative, Integer uncertainForward,
            Integer uncertainNegative, Integer totalForward, Integer answered,
            Integer unanswered, Integer generatedInappropriateContent, Integer rejected,
            Integer notRejected, Integer positiveGuidance, Integer totalNegative,
            Integer total, Double nonRejected, Double shouldReject) {
        this.unjudgedForward = unjudgedForward;
        this.unjudgedNegative = unjudgedNegative;
        this.uncertainForward = uncertainForward;
        this.uncertainNegative = uncertainNegative;
        this.totalForward = totalForward;
        this.answered = answered;
        this.unanswered = unanswered;
        this.generatedInappropriateContent = generatedInappropriateContent;
        this.rejected = rejected;
        this.notRejected = notRejected;
        this.positiveGuidance = positiveGuidance;
        this.totalNegative = totalNegative;
        this.total = total;
        this.nonRejected = nonRejected;
        this.shouldReject = shouldReject;
    }
}
