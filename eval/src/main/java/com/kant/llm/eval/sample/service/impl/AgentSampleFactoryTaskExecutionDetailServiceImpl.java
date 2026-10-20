package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskExecutionDetailDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskExecutionDetailMapper;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskExecutionDetailPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskExecutionDetailVO;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskExecutionDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AgentSampleFactoryTaskExecutionDetailServiceImpl extends ServiceImpl<AgentSampleFactoryTaskExecutionDetailMapper, AgentSampleFactoryTaskExecutionDetailDO> implements AgentSampleFactoryTaskExecutionDetailService {

    @Override
    public List<SampleFactoryTaskExecutionDetailVO> listSampleFactoryTaskExecutionDetails(String taskId) {
        return list(Wrappers.<AgentSampleFactoryTaskExecutionDetailDO>lambdaQuery()
                .eq(AgentSampleFactoryTaskExecutionDetailDO::getTaskId, taskId)
                .orderByDesc(AgentSampleFactoryTaskExecutionDetailDO::getCreateTime)
                .orderByDesc(AgentSampleFactoryTaskExecutionDetailDO::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public Page<SampleFactoryTaskExecutionDetailVO> pageSampleFactoryTaskExecutionDetails(SampleFactoryTaskExecutionDetailPageRequest request) {
        if (request == null || !StringUtils.hasText(request.getTaskId())) {
            throw new ServiceException("样本生产任务ID不能为空");
        }
        Integer current = request.getCurrent();
        Integer size = request.getSize();
        int pageCurrent = current == null || current <= 0 ? 1 : current;
        int pageSize = size == null || size <= 0 ? 10 : size;
        Page<AgentSampleFactoryTaskExecutionDetailDO> detailPage = page(new Page<>(pageCurrent, pageSize),
                Wrappers.<AgentSampleFactoryTaskExecutionDetailDO>lambdaQuery()
                        .eq(AgentSampleFactoryTaskExecutionDetailDO::getTaskId, request.getTaskId())
                        .orderByDesc(AgentSampleFactoryTaskExecutionDetailDO::getCreateTime)
                        .orderByDesc(AgentSampleFactoryTaskExecutionDetailDO::getId));
        Page<SampleFactoryTaskExecutionDetailVO> result = new Page<>(
                detailPage.getCurrent(), detailPage.getSize(), detailPage.getTotal());
        result.setRecords(detailPage.getRecords().stream().map(this::convertToVO).toList());
        return result;
    }

    private SampleFactoryTaskExecutionDetailVO convertToVO(AgentSampleFactoryTaskExecutionDetailDO detail) {
        SampleFactoryTaskExecutionDetailVO vo = new SampleFactoryTaskExecutionDetailVO();
        BeanUtils.copyProperties(detail, vo);
        return vo;
    }
}
