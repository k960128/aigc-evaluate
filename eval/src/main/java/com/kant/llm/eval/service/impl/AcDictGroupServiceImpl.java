package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.AcDictGroupDO;
import com.kant.llm.eval.dao.mapper.AcDictGroupMapper;
import com.kant.llm.eval.service.AcDictGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AcDictGroupServiceImpl extends ServiceImpl<AcDictGroupMapper, AcDictGroupDO> implements AcDictGroupService {
}