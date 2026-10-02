package com.kant.llm.eval.agent.validation.support;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
/** D04受限快照序列化和稳定内容摘要；不输出正文/SQL到接口错误。 */
@Component
public class D04JsonSupport {
    private final ObjectMapper mapper;
    public D04JsonSupport(ObjectMapper mapper) { this.mapper=mapper; }
    public String write(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException ex) { throw failure(); } }
    public <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw failure(); } }
    public <T> T read(String value,TypeReference<T> type) { try { return mapper.readValue(value,type); } catch(JsonProcessingException ex) { throw failure(); } }
    public String hash(Object value) { return digest(write(value)); }
    public String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256不可用",ex); } }
    public Instant instant(LocalDateTime time) { return time==null?null:time.atZone(ZoneId.systemDefault()).toInstant(); }
    public LocalDateTime local(Instant time) { return time==null?null:LocalDateTime.ofInstant(time,ZoneId.systemDefault()); }
    private ClientException failure() { return new ClientException("D04受限事实解析失败",AgentFindingErrorCode.PERSISTENCE_ERROR); }
}
