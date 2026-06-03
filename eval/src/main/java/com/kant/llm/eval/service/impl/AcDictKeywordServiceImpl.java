package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.AcDictKeywordDO;
import com.kant.llm.eval.dao.mapper.AcDictKeywordMapper;
import com.kant.llm.eval.service.AcDictKeywordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AcDictKeywordServiceImpl extends ServiceImpl<AcDictKeywordMapper, AcDictKeywordDO> implements AcDictKeywordService {
}