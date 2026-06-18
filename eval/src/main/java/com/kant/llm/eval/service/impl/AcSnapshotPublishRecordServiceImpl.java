package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.AcSnapshotPublishRecordDO;
import com.kant.llm.eval.dao.mapper.AcSnapshotPublishRecordMapper;
import com.kant.llm.eval.service.AcSnapshotPublishRecordService;
import org.springframework.stereotype.Service;

@Service
public class AcSnapshotPublishRecordServiceImpl
        extends ServiceImpl<AcSnapshotPublishRecordMapper, AcSnapshotPublishRecordDO>
        implements AcSnapshotPublishRecordService {
}
