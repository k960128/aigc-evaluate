package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.ModelManufacturerDO;
import com.kant.llm.eval.dao.mapper.ModelManufacturerMapper;
import com.kant.llm.eval.service.ModelManufacturerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ModelManufacturerServiceImpl extends ServiceImpl<ModelManufacturerMapper, ModelManufacturerDO> implements ModelManufacturerService {
}