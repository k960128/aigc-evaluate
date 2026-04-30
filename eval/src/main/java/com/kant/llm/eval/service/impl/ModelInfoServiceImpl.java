package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ModelInfoServiceImpl extends ServiceImpl<ModelInfoMapper, ModelInfoDO> implements ModelInfoService {
}