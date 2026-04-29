package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskVocabularyDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyMapper;
import com.kant.llm.eval.service.RiskVocabularyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskVocabularyServiceImpl extends ServiceImpl<RiskVocabularyMapper, RiskVocabularyDO> implements RiskVocabularyService {
}