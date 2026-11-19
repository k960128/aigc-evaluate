package com.kant.llm.eval.basesource.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.basesource.dao.entity.JudgeModelInfoDO;
import com.kant.llm.eval.basesource.dao.mapper.JudgeModelInfoMapper;
import com.kant.llm.eval.basesource.service.JudgeModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JudgeModelInfoServiceImpl
        extends ServiceImpl<JudgeModelInfoMapper, JudgeModelInfoDO>
        implements JudgeModelInfoService {
}