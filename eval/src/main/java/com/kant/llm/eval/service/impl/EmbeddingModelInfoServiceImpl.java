package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.EmbeddingModelInfoDO;
import com.kant.llm.eval.dao.mapper.EmbeddingModelInfoMapper;
import com.kant.llm.eval.service.EmbeddingModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmbeddingModelInfoServiceImpl extends ServiceImpl<EmbeddingModelInfoMapper, EmbeddingModelInfoDO> implements EmbeddingModelInfoService {
}
