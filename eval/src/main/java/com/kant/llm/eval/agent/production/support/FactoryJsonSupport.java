package com.kant.llm.eval.agent.production.support;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.production.error.SampleFactoryErrorCode;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
/** 生产配置序列化、内容摘要与UTC数据库时间转换；不把受限正文带入错误。 */
@Component
public class FactoryJsonSupport {
    /**
     * 该工具统一D05的持久化边界：JSON解析失败必须暴露为事实损坏，不能用默认值继续执行；
     * 摘要用于幂等、完整性和配置指纹，不能被误当作加密或权限控制。
     */
    /** 复用应用JSON配置，持久化契约变化应保持历史可读性。 */
    private final ObjectMapper mapper;
    /** 注入序列化器，不在工具内改变全局JSON配置。 */
    public FactoryJsonSupport(ObjectMapper mapper) { this.mapper=mapper; }
    /** 原样序列化契约；异常只返回固定错误，不暴露对象中的受限正文。 */
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException ex) { throw error(); } }
    /** 恢复具体记录类型，解析失败不使用默认对象掩盖数据异常。 */
    public <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw error(); } }
    /** 恢复带泛型的集合契约，例如正式用例版本引用列表。 */
    public <T> T read(String value,TypeReference<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw error(); } }
    /** 对JSON文本计算摘要，本方法不做字段排序；需稳定指纹的调用方必须传入稳定字段顺序。 */
    public String hash(Object value) { return digest(write(value)); }
    /** 对原字符串的UTF-8字节计算SHA-256，不trim或规范化，也不作为加密存储替代品。 */
    public String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256不可用",ex); } }
    /** 与DATETIME(6)统一到微秒，禁止依赖应用服务器默认时区。 */
    public LocalDateTime utc(Instant value) { return value==null?null:LocalDateTime.ofInstant(value,ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    /** 将数据库无时区值按约定UTC恢复为时间点，null原样保留，展示时区由前端处理。 */
    public Instant instant(LocalDateTime value) { return value==null?null:value.toInstant(ZoneOffset.UTC); }
    /** 解析异常统一收口，禁止带入原JSON、SQL或供应商消息。 */
    private ClientException error() { return new ClientException("样本生产事实解析失败",SampleFactoryErrorCode.PERSISTENCE_ERROR); }
}
