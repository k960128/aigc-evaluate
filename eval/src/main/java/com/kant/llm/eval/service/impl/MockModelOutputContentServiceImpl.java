package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.MockModelOutputContent;
import com.kant.llm.eval.dao.mapper.MockModelOutputContentMapper;
import com.kant.llm.eval.service.MockModelOutputContentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class MockModelOutputContentServiceImpl extends ServiceImpl<MockModelOutputContentMapper, MockModelOutputContent> implements MockModelOutputContentService {
}
