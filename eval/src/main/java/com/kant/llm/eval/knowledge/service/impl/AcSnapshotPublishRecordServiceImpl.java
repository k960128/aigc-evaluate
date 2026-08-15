package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotPublishRecordDO;
import com.kant.llm.eval.knowledge.dao.mapper.AcSnapshotPublishRecordMapper;
import com.kant.llm.eval.knowledge.service.AcSnapshotPublishRecordService;
import org.springframework.stereotype.Service;

@Service
public class AcSnapshotPublishRecordServiceImpl
        extends ServiceImpl<AcSnapshotPublishRecordMapper, AcSnapshotPublishRecordDO>
        implements AcSnapshotPublishRecordService {
}
