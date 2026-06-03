package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskVocabularyGroupDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyGroupMapper;
import com.kant.llm.eval.service.RiskVocabularyGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskVocabularyGroupServiceImpl extends ServiceImpl<RiskVocabularyGroupMapper, RiskVocabularyGroupDO> implements RiskVocabularyGroupService {
}