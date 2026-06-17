package com.kant.llm.eval.common.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.ai.infra.embedding.EmbeddingModelInfo;
import com.kant.ai.infra.embedding.EmbeddingModelInfoProvider;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.llm.eval.dao.entity.EmbeddingModelInfoDO;
import com.kant.llm.eval.dao.mapper.EmbeddingModelInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 基于数据库的 Embedding 模型配置提供者。
 *
 * <p>只负责把 eval 模块中的 embedding_model_info 表转换成 infra-ai 的轻量配置对象，
 * 避免 infra-ai 反向依赖 eval 的 Mapper 或 DO。</p>
 */
@Slf4j
@Component
public class DatabaseEmbeddingModelInfoProvider implements EmbeddingModelInfoProvider {

    private final EmbeddingModelInfoMapper embeddingModelInfoMapper;

    public DatabaseEmbeddingModelInfoProvider(EmbeddingModelInfoMapper embeddingModelInfoMapper) {
        this.embeddingModelInfoMapper = embeddingModelInfoMapper;
    }

    @Override
    public List<EmbeddingModelInfo> listEnabledEmbeddingModels() {
        LambdaQueryWrapper<EmbeddingModelInfoDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EmbeddingModelInfoDO::getStatus, true)
                .orderByAsc(EmbeddingModelInfoDO::getId);
        return embeddingModelInfoMapper.selectList(queryWrapper).stream()
                .map(this::convertToEmbeddingModelInfo)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EmbeddingModelInfo::getModelId,
                        Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private EmbeddingModelInfo convertToEmbeddingModelInfo(EmbeddingModelInfoDO entity) {
        ModelManufacturerEnum manufacturerType = parseManufacturer(entity.getManufacturerCode(), entity.getId());
        if (manufacturerType == null) {
            return null;
        }
        return EmbeddingModelInfo.builder()
                .modelId(entity.getId())
                .model(entity.getModel())
                .modelCode(entity.getModelCode())
                .apiKey(entity.getApiKey())
                .baseUrl(entity.getBaseUrl())
                .dimension(entity.getDimension())
                .maxInputTokens(entity.getMaxInputTokens())
                .batchSize(entity.getBatchSize())
                .distanceMetric(entity.getDistanceMetric())
                .normalize(entity.getNormalize())
                .config(entity.getConfig())
                .status(entity.getStatus())
                .manufacturerType(manufacturerType)
                .build();
    }

    private ModelManufacturerEnum parseManufacturer(String manufacturerCode, Long modelId) {
        try {
            return ModelManufacturerEnum.valueOf(manufacturerCode.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            log.warn("Embedding 模型厂商编码非法，跳过该模型配置，modelId: {}, manufacturerCode: {}",
                    modelId, manufacturerCode);
            return null;
        }
    }
}
