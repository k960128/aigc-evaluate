package com.kant.llm.eval.sample.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 变异战术树节点
 */
@Data
public class MutationTacticsTreeVO {

    /** 主键ID */
    private Long id;

    /** 变异战术ID */
    private Long mutationTacticsId;

    /** 变异战术父级ID，0 表示根节点 */
    private Long mutationTacticsParentId;

    /** 变异战术唯一编码 */
    private String mutationTacticsCode;

    /** 变异战术名称 */
    private String mutationTacticsName;

    /** 原理解释 */
    private String explain;

    /** 变异示例 */
    private String example;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    /** 子节点，叶子节点返回空数组 */
    private List<MutationTacticsTreeVO> children = new ArrayList<>();
}
