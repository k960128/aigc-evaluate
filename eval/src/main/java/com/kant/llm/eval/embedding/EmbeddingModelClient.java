package com.kant.llm.eval.embedding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.EmbeddingModelInfoDO;
import com.kant.llm.eval.service.EmbeddingModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌入模型客户端。
 *
 * <p>负责从启用的嵌入模型配置中懒加载一个 {@link EmbeddingModel}，
 * 并提供单文本和批量文本向量化能力。</p>
 */
@Slf4j
@Service
public class EmbeddingModelClient {

    /**
     * 嵌入模型配置服务，用于读取 embedding_model_info 表中的启用配置。
     */
    private final EmbeddingModelInfoService embeddingModelInfoService;

    /**
     * 懒加载缓存的模型持有器，同时保存模型实例和构建模型时使用的配置。
     */
    private volatile EmbeddingModelHolder embeddingModelHolder;

    /**
     * 创建嵌入模型客户端。
     *
     * @param embeddingModelInfoService 嵌入模型配置服务
     */
    public EmbeddingModelClient(EmbeddingModelInfoService embeddingModelInfoService) {
        this.embeddingModelInfoService = embeddingModelInfoService;
    }

    /**
     * 将输入文本转换为嵌入向量。
     *
     * <p>首次调用时会初始化并缓存模型；后续调用复用缓存模型。
     * 如果配置要求归一化，则会对模型返回的向量执行 L2 归一化。</p>
     *
     * @param input 输入文本
     * @return 嵌入向量
     */
    public float[] embed(String input) {
        validateInput(input);
        EmbeddingModelHolder holder = getEmbeddingModelHolder();
        return embedSingle(holder, input);
    }

    /**
     * 将多个输入文本批量转换为嵌入向量。
     *
     * <p>当模型配置了有效的 batchSize 时，会按 batchSize 自动拆分请求；
     * 返回向量顺序与输入文本顺序保持一致。</p>
     *
     * @param inputs 输入文本列表
     * @return 嵌入向量列表
     */
    public List<float[]> embed(List<String> inputs) {
        validateInputs(inputs);
        EmbeddingModelHolder holder = getEmbeddingModelHolder();
        int batchSize = resolveBatchSize(holder.embeddingModelInfoDO(), inputs.size());
        List<float[]> vectors = new ArrayList<>(inputs.size());

        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            vectors.addAll(embedBatch(holder, inputs.subList(start, end)));
        }
        return vectors;
    }

    /**
     * 获取当前启用的 Spring AI EmbeddingModel。
     *
     * <p>真实向量库复用同一个懒加载模型，避免写入和查询使用不同的 embedding 配置。</p>
     *
     * @return Spring AI embedding 模型实例
     */
    public EmbeddingModel getEmbeddingModel() {
        return getEmbeddingModelHolder().embeddingModel();
    }

    /**
     * 获取当前启用 embedding 模型的向量维度。
     *
     * @return 已配置的向量维度，未配置时返回 null
     */
    public Integer getDimension() {
        return getEmbeddingModelHolder().embeddingModelInfoDO().getDimension();
    }
    /**
     * 调用模型执行单文本向量化。
     *
     * @param holder 嵌入模型持有器
     * @param input  输入文本
     * @return 嵌入向量
     */
    private float[] embedSingle(EmbeddingModelHolder holder, String input) {
        float[] vector;
        try {
            vector = holder.embeddingModel().embed(input);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("嵌入模型调用失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        }

        validateVector(vector, holder.embeddingModelInfoDO());
        if (Boolean.TRUE.equals(holder.embeddingModelInfoDO().getNormalize())) {
            return normalize(vector);
        }
        return vector;
    }

    /**
     * 调用模型执行一批文本向量化。
     *
     * @param holder 嵌入模型持有器
     * @param inputs 当前批次输入文本列表
     * @return 当前批次嵌入向量列表
     */
    private List<float[]> embedBatch(EmbeddingModelHolder holder, List<String> inputs) {
        List<float[]> vectors;
        try {
            vectors = holder.embeddingModel().embed(inputs);
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("嵌入模型批量调用失败：" + ex.getMessage(), ex, BaseErrorCode.REMOTE_ERROR);
        }

        validateVectorList(vectors, inputs.size());
        List<float[]> processedVectors = new ArrayList<>(vectors.size());
        for (float[] vector : vectors) {
            validateVector(vector, holder.embeddingModelInfoDO());
            if (Boolean.TRUE.equals(holder.embeddingModelInfoDO().getNormalize())) {
                processedVectors.add(normalize(vector));
            } else {
                processedVectors.add(vector);
            }
        }
        return processedVectors;
    }

    /**
     * 获取已缓存的嵌入模型持有器。
     *
     * <p>使用双重检查锁保证并发场景下模型只初始化一次。</p>
     *
     * @return 嵌入模型持有器
     */
    private EmbeddingModelHolder getEmbeddingModelHolder() {
        EmbeddingModelHolder holder = embeddingModelHolder;
        if (holder == null) {
            synchronized (this) {
                holder = embeddingModelHolder;
                if (holder == null) {
                    holder = buildEmbeddingModelHolder();
                    embeddingModelHolder = holder;
                }
            }
        }
        return holder;
    }

    /**
     * 根据启用的嵌入模型配置构建模型持有器。
     *
     * @return 嵌入模型持有器
     */
    private EmbeddingModelHolder buildEmbeddingModelHolder() {
        EmbeddingModelInfoDO embeddingModelInfoDO = loadEnabledEmbeddingModelInfo();
        validateEmbeddingModelInfo(embeddingModelInfoDO);
        EmbeddingModel embeddingModel = buildEmbeddingModel(embeddingModelInfoDO);
        log.info("Embedding model initialized, model={}, dimension={}, normalize={}",
                embeddingModelInfoDO.getModel(),
                embeddingModelInfoDO.getDimension(),
                embeddingModelInfoDO.getNormalize());
        return new EmbeddingModelHolder(embeddingModel, embeddingModelInfoDO);
    }

    /**
     * 构建 Spring AI 嵌入模型实例。
     *
     * <p>只有配置了大于 0 的维度时才向模型选项写入 dimensions。</p>
     *
     * @param embeddingModelInfoDO 嵌入模型配置
     * @return 嵌入模型实例
     */
    private EmbeddingModel buildEmbeddingModel(EmbeddingModelInfoDO embeddingModelInfoDO) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(embeddingModelInfoDO.getApiKey())
                .baseUrl(embeddingModelInfoDO.getBaseUrl())
                .build();

        OpenAiEmbeddingOptions embeddingOptions;
        if (isValidDimension(embeddingModelInfoDO.getDimension())) {
            embeddingOptions = OpenAiEmbeddingOptions.builder()
                    .model(embeddingModelInfoDO.getModel())
                    .dimensions(embeddingModelInfoDO.getDimension())
                    .build();
        } else {
            embeddingOptions = OpenAiEmbeddingOptions.builder()
                    .model(embeddingModelInfoDO.getModel())
                    .build();
        }

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    /**
     * 查询当前启用的嵌入模型配置。
     *
     * <p>当存在多条启用配置时，固定选择 id 最小的一条。</p>
     *
     * @return 启用的嵌入模型配置
     */
    private EmbeddingModelInfoDO loadEnabledEmbeddingModelInfo() {
        List<EmbeddingModelInfoDO> embeddingModelInfoDOS = embeddingModelInfoService.list(
                new LambdaQueryWrapper<EmbeddingModelInfoDO>()
                        .eq(EmbeddingModelInfoDO::getStatus, Boolean.TRUE)
                        .orderByAsc(EmbeddingModelInfoDO::getId)
                        .last("LIMIT 1"));
        if (embeddingModelInfoDOS == null || embeddingModelInfoDOS.isEmpty()) {
            throw new ServiceException("未配置启用的嵌入模型");
        }
        return embeddingModelInfoDOS.getFirst();
    }

    /**
     * 校验输入文本。
     *
     * @param input 输入文本
     */
    private void validateInput(String input) {
        if (StringUtils.isBlank(input)) {
            throw new ServiceException("嵌入文本不能为空");
        }
    }

    /**
     * 校验批量输入文本。
     *
     * @param inputs 输入文本列表
     */
    private void validateInputs(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new ServiceException("批量嵌入文本不能为空");
        }
        for (int i = 0; i < inputs.size(); i++) {
            if (StringUtils.isBlank(inputs.get(i))) {
                throw new ServiceException("批量嵌入文本不能为空，位置：" + i);
            }
        }
    }

    /**
     * 校验嵌入模型必要配置。
     *
     * @param embeddingModelInfoDO 嵌入模型配置
     */
    private void validateEmbeddingModelInfo(EmbeddingModelInfoDO embeddingModelInfoDO) {
        if (StringUtils.isBlank(embeddingModelInfoDO.getModel())) {
            throw new ServiceException("嵌入模型名称不能为空");
        }
        if (StringUtils.isBlank(embeddingModelInfoDO.getBaseUrl())) {
            throw new ServiceException("嵌入模型 BaseUrl 不能为空");
        }
        if (StringUtils.isBlank(embeddingModelInfoDO.getApiKey())) {
            throw new ServiceException("嵌入模型 ApiKey 不能为空");
        }
    }

    /**
     * 校验模型返回的向量。
     *
     * <p>向量不能为空；如果配置了有效维度，则返回向量长度必须与配置一致。</p>
     *
     * @param vector               模型返回向量
     * @param embeddingModelInfoDO 嵌入模型配置
     */
    private void validateVector(float[] vector, EmbeddingModelInfoDO embeddingModelInfoDO) {
        if (vector == null || vector.length == 0) {
            throw new ServiceException("嵌入模型返回向量为空", BaseErrorCode.REMOTE_ERROR);
        }

        Integer dimension = embeddingModelInfoDO.getDimension();
        if (isValidDimension(dimension) && vector.length != dimension) {
            throw new ServiceException("嵌入模型返回向量维度不匹配，期望：" + dimension + "，实际：" + vector.length,
                    BaseErrorCode.REMOTE_ERROR);
        }
    }

    /**
     * 校验模型批量返回的向量列表。
     *
     * <p>返回列表不能为空，且返回数量必须与当前批次输入数量一致。</p>
     *
     * @param vectors           模型返回向量列表
     * @param expectedListSize  当前批次输入数量
     */
    private void validateVectorList(List<float[]> vectors, int expectedListSize) {
        if (vectors == null || vectors.isEmpty()) {
            throw new ServiceException("嵌入模型批量返回向量为空", BaseErrorCode.REMOTE_ERROR);
        }
        if (vectors.size() != expectedListSize) {
            throw new ServiceException("嵌入模型批量返回向量数量不匹配，期望：" + expectedListSize + "，实际：" + vectors.size(),
                    BaseErrorCode.REMOTE_ERROR);
        }
    }

    /**
     * 对向量执行 L2 归一化。
     *
     * <p>当向量范数为 0 时原样返回，避免除零。</p>
     *
     * @param vector 待归一化向量
     * @return 归一化后的向量
     */
    private float[] normalize(float[] vector) {
        double norm = 0D;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm == 0D) {
            return vector;
        }

        double denominator = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / denominator);
        }
        return vector;
    }

    /**
     * 判断维度配置是否有效。
     *
     * @param dimension 维度配置
     * @return true-有效，false-无效
     */
    private boolean isValidDimension(Integer dimension) {
        return dimension != null && dimension > 0;
    }

    /**
     * 解析批量请求大小。
     *
     * <p>配置 batchSize 大于 0 时按配置拆分；未配置时一次处理全部输入。</p>
     *
     * @param embeddingModelInfoDO 嵌入模型配置
     * @param inputSize            输入文本数量
     * @return 当前可使用的批量大小
     */
    private int resolveBatchSize(EmbeddingModelInfoDO embeddingModelInfoDO, int inputSize) {
        Integer batchSize = embeddingModelInfoDO.getBatchSize();
        if (batchSize != null && batchSize > 0) {
            return Math.min(batchSize, inputSize);
        }
        return inputSize;
    }

    /**
     * 嵌入模型缓存对象。
     *
     * @param embeddingModel       嵌入模型实例
     * @param embeddingModelInfoDO 构建模型时使用的配置
     */
    private record EmbeddingModelHolder(EmbeddingModel embeddingModel, EmbeddingModelInfoDO embeddingModelInfoDO) {
    }
}
