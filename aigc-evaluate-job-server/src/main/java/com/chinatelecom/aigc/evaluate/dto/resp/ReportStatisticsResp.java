package com.chinatelecom.aigc.evaluate.dto.resp;

import com.chinatelecom.aigc.evaluate.service.impl.TagStatisticsResp;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
public class ReportStatisticsResp {

    // 统计信息字段
    private String name;   // 题目类别名称，例如 "FORWARD" 或 "NEGATIVE"
    private long total;    // 该类别的题目总数
    private long hard;     // 难度为 "hard" 的题目数量
    private long medium;   // 难度为 "medium" 的题目数量
    private long simple;   // 难度为 "simple" 的题目数量
    private String per;
    private String result;
    private String standard;
    @Setter
    @Getter
    private List<TagStatisticsResp> tagStatistics; // 新增：标签统计
    @Setter
    private Map<String, Long> attackMethodStatistics;


    public Map<String, Long> getAttackMethodStatistics() {
        return attackMethodStatistics;
    }

}
