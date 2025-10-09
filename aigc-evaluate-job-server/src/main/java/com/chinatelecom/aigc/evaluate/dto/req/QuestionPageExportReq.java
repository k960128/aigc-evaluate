package com.chinatelecom.aigc.evaluate.dto.req;

import com.chinatelecom.aigc.evaluate.common.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "管理后台 - 题目分页 Export Request Req")
public class QuestionPageExportReq extends PageParam {

    @ApiModelProperty("题目唯一编号集合")
    private List<String> questionIds = new ArrayList<>();
}
