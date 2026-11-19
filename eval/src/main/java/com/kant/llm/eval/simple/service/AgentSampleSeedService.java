package com.kant.llm.eval.simple.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.simple.dao.entity.AgentSampleSeedDO;
import com.kant.llm.eval.simple.dto.SimpleSeedFactoryRequests;
import com.kant.llm.eval.simple.dto.SimpleSeedFactoryResponses;

import java.util.List;

public interface AgentSampleSeedService extends IService<AgentSampleSeedDO> {

    List<SimpleSeedFactoryResponses.Seed> seeds();

    SimpleSeedFactoryResponses.Seed createSeed(SimpleSeedFactoryRequests.Seed request);

    SimpleSeedFactoryResponses.Content seedContent(String seedId);
}
