package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleMutationTacticsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleMutationTacticsMapper;
import com.kant.llm.eval.sample.dto.MutationTacticsTreeVO;
import com.kant.llm.eval.sample.service.AgentSampleMutationTacticsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgentSampleMutationTacticsServiceImpl extends ServiceImpl<AgentSampleMutationTacticsMapper, AgentSampleMutationTacticsDO> implements AgentSampleMutationTacticsService {

    @Override
    public List<MutationTacticsTreeVO> getMutationTacticsTree() {
        List<AgentSampleMutationTacticsDO> tactics = list(Wrappers.<AgentSampleMutationTacticsDO>lambdaQuery()
                .orderByAsc(AgentSampleMutationTacticsDO::getMutationTacticsId)
                .orderByAsc(AgentSampleMutationTacticsDO::getId));

        List<MutationTacticsTreeVO> nodes = new ArrayList<>(tactics.size());
        Map<Long, MutationTacticsTreeVO> nodesByTacticsId = new HashMap<>();
        for (AgentSampleMutationTacticsDO tactic : tactics) {
            MutationTacticsTreeVO node = new MutationTacticsTreeVO();
            BeanUtils.copyProperties(tactic, node);
            nodes.add(node);
            nodesByTacticsId.put(node.getMutationTacticsId(), node);
        }

        List<MutationTacticsTreeVO> roots = new ArrayList<>();
        for (MutationTacticsTreeVO node : nodes) {
            Long parentId = node.getMutationTacticsParentId();
            MutationTacticsTreeVO parent = nodesByTacticsId.get(parentId);
            if (parentId == null || parentId == 0L || parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }
}
