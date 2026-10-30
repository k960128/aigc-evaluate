package com.kant.llm.eval.simple.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.simple.dao.entity.AgentSampleMutationTacticsDO;
import com.kant.llm.eval.simple.dto.MutationTacticsTreeVO;

import java.util.List;

public interface AgentSampleMutationTacticsService extends IService<AgentSampleMutationTacticsDO> {

    /**
     * 查询未逻辑删除的变异战术，按战术ID、主键ID升序返回树形列表。
     * 父级ID为 0、为空或父节点不存在时，作为根节点返回。
     *
     * @return 变异战术树，查询结果为空时返回空列表
     */
    List<MutationTacticsTreeVO> getMutationTacticsTree();
}
