package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyKeywordMapper;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskVocabularyKeywordServiceImpl extends ServiceImpl<RiskVocabularyKeywordMapper, RiskVocabularyKeywordDO> implements RiskVocabularyKeywordService {
}
