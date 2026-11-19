package com.kant.llm.eval.knowledge.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionSnapshotDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionMapper;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionSnapshotMapper;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureCurrentVersionVO;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KnowledgeFeatureDocumentVersionServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentVersionMapper, KnowledgeFeatureDocumentVersionDO>
        implements KnowledgeFeatureDocumentVersionService {

    private static final String KNOWLEDGE_FEATURE_CURRENT_VERSION_KEY = "knowledge_feature:version:current_version_id";

    private final StringRedisTemplate redisTemplate;
    private final KnowledgeFeatureDocumentVersionSnapshotMapper knowledgeFeatureDocumentVersionSnapshotMapper;

    public KnowledgeFeatureDocumentVersionServiceImpl(StringRedisTemplate redisTemplate,
                                                    KnowledgeFeatureDocumentVersionSnapshotMapper knowledgeFeatureDocumentVersionSnapshotMapper) {
        this.redisTemplate = redisTemplate;
        this.knowledgeFeatureDocumentVersionSnapshotMapper = knowledgeFeatureDocumentVersionSnapshotMapper;
    }

    @Override
    public void updateCurrentVersion(Long currentVersionId) {
        KnowledgeFeatureDocumentVersionDO documentVersionDO = getOne(null);
        documentVersionDO.setCurrentVersionId(currentVersionId);
        updateById(documentVersionDO);

    }

    @Override
    public KnowledgeFeatureCurrentVersionVO getCurrentVersionCache() {
        String cache = redisTemplate.opsForValue().get(KNOWLEDGE_FEATURE_CURRENT_VERSION_KEY);
        if (StringUtils.isNotBlank(cache)) {
            return JSONObject.parseObject(cache,KnowledgeFeatureCurrentVersionVO.class);
        }
        List<KnowledgeFeatureDocumentVersionDO> list = list();
        if(list.isEmpty()){
            return null;
        }
        KnowledgeFeatureDocumentVersionDO documentVersionDO = list.get(0);

        KnowledgeFeatureDocumentVersionSnapshotDO snapshotDO = knowledgeFeatureDocumentVersionSnapshotMapper.selectById(documentVersionDO.getCurrentVersionId());

        redisTemplate.opsForValue().set("knowledge_feature:version:current_version_id",
                JSONObject.toJSONString(KnowledgeFeatureCurrentVersionVO.builder()
                        .currentVersion(snapshotDO.getVersion())
                        .currentVersionId(snapshotDO.getVersionId())
                        .build()));

        return KnowledgeFeatureCurrentVersionVO.builder()
                .currentVersion(documentVersionDO.getLockVersion())
                .currentVersionId(documentVersionDO.getCurrentVersionId())
                .build();
    }
}
