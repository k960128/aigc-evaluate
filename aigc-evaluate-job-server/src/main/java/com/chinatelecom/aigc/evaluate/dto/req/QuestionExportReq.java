package com.chinatelecom.aigc.evaluate.dto.req;


import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
public class QuestionExportReq {

    @ApiModelProperty("题目唯一编号集合")
    private List<String> questionIds = new ArrayList<>();
}
