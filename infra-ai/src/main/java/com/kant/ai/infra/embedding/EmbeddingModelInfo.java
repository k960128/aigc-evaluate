package com.kant.ai.infra.embedding;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelInfo {

    private Long modelId;
    private String model;
    private String apiKey;
    private String baseUrl;
    /**
     * 模型厂商标识
     */
    private ModelManufacturerEnum manufacturerType;
}
