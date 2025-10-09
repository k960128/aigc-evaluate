package com.chinatelecom.aigc.evaluate.dto.resp;

import lombok.Data;

import java.util.List;

@Data
public class TagSummaryResp {
    private String name;
    private Long count;
    private List<TagChildResp> children;

    // 内部静态类，作为子标签数据结构
    @Data
    public static class TagChildResp {
        private String name;
        private Long count;
        private String type;

        // getter/setter
    }
}

