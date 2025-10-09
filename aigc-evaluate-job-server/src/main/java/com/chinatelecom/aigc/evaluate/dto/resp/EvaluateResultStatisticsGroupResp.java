package com.chinatelecom.aigc.evaluate.dto.resp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EvaluateResultStatisticsGroupResp {
    private EvaluateResultStatisticsResp manual;
    private EvaluateResultStatisticsResp auto;
}
