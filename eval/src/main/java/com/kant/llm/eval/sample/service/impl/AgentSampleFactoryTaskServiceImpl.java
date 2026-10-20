package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskMapper;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskVO;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AgentSampleFactoryTaskServiceImpl extends ServiceImpl<AgentSampleFactoryTaskMapper, AgentSampleFactoryTaskDO> implements AgentSampleFactoryTaskService {

    @Override
    public List<SampleFactoryTaskVO> listSampleFactoryTasks() {
        return list(Wrappers.<AgentSampleFactoryTaskDO>lambdaQuery()
                .orderByDesc(AgentSampleFactoryTaskDO::getCreateTime)
                .orderByDesc(AgentSampleFactoryTaskDO::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public Page<SampleFactoryTaskVO> pageSampleFactoryTasks(SampleFactoryTaskPageRequest request) {
        SampleFactoryTaskPageRequest queryRequest = request == null ? new SampleFactoryTaskPageRequest() : request;
        Integer current = queryRequest.getCurrent();
        Integer size = queryRequest.getSize();
        long pageCurrent = current != null && current > 0 ? current : 1L;
        long pageSize = size != null && size > 0 ? size : 10L;
        Page<AgentSampleFactoryTaskDO> pageResult = page(new Page<>(pageCurrent, pageSize),
                Wrappers.<AgentSampleFactoryTaskDO>lambdaQuery()
                        .orderByDesc(AgentSampleFactoryTaskDO::getCreateTime)
                        .orderByDesc(AgentSampleFactoryTaskDO::getId));
        Page<SampleFactoryTaskVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToVO)
                .toList());
        return voPage;
    }

    private SampleFactoryTaskVO convertToVO(AgentSampleFactoryTaskDO task) {
        SampleFactoryTaskVO vo = new SampleFactoryTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
