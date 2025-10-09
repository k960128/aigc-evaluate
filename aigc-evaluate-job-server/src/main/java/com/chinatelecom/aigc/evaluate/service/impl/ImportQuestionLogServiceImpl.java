package com.chinatelecom.aigc.evaluate.service.impl;

import com.chinatelecom.aigc.evaluate.common.enums.ImportCommonEnum;
import com.chinatelecom.aigc.evaluate.domain.ImportQuestionLogDO;
import com.chinatelecom.aigc.evaluate.mapper.ImportQuestionLogMapper;
import com.chinatelecom.aigc.evaluate.service.ImportQuestionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImportQuestionLogServiceImpl implements ImportQuestionLogService {

    private final ImportQuestionLogMapper importQuestionLogMapper;

    public ImportQuestionLogServiceImpl(ImportQuestionLogMapper importQuestionLogMapper) {
        this.importQuestionLogMapper = importQuestionLogMapper;
    }

    @Override
    public Boolean getRunImportTask() {
        Long rowCount = importQuestionLogMapper.selectCount(ImportQuestionLogDO::getRunState, ImportCommonEnum.START.name());
        return rowCount >= 1;
    }

    @Override
    public ImportQuestionLogDO create(ImportQuestionLogDO importQuestionLogDO) {
        importQuestionLogMapper.insert(importQuestionLogDO);
        return importQuestionLogDO;
    }

    @Override
    public void update(ImportQuestionLogDO importQuestionLogDO) {
        importQuestionLogMapper.updateById(importQuestionLogDO);
    }
}
