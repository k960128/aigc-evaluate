package com.chinatelecom.aigc.evaluate.dto.resp;

import lombok.Data;

@Data
public class ReportPerStatisticResp {
    private long total;
    private long qualified;
    private String name;
    private double per;     // 成功率百分比
}
