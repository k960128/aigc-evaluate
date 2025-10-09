package com.chinatelecom.aigc.evaluate.dto.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = false) // 设置 chain = false
public class ExtractStep implements Serializable {

    /**
     * 题集ID
     */
    private Long questionSetId;
    /**
     * 题库(正向|负向..)
     */
    private String category;

    private ExtractConfDifficulty difficultyConf;

    private ExtractConfRandom randomConf;

    private ExtractConfCustom customConf;

    private Set<String> questionIdSet = new HashSet<>();
}
