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
    private final ObjectMapper mapper;
    public FactoryJsonSupport(ObjectMapper mapper) { this.mapper=mapper; }
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException ex) { throw error(); } }
    public <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw error(); } }
    public <T> T read(String value,TypeReference<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw error(); } }
    public String hash(Object value) { return digest(write(value)); }
    public String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256不可用",ex); } }
    /** 与DATETIME(6)统一到微秒，禁止依赖应用服务器默认时区。 */
    public LocalDateTime utc(Instant value) { return value==null?null:LocalDateTime.ofInstant(value,ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public Instant instant(LocalDateTime value) { return value==null?null:value.toInstant(ZoneOffset.UTC); }
    private ClientException error() { return new ClientException("样本生产事实解析失败",SampleFactoryErrorCode.PERSISTENCE_ERROR); }
}
