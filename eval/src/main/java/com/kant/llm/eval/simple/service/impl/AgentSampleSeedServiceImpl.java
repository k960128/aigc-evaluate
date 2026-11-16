package com.kant.llm.eval.simple.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.simple.dao.entity.AgentSampleSeedDO;
import com.kant.llm.eval.simple.dao.mapper.AgentSampleSeedMapper;
import com.kant.llm.eval.simple.dto.SimpleSeedFactoryRequests;
import com.kant.llm.eval.simple.dto.SimpleSeedFactoryResponses;
import com.kant.llm.eval.simple.error.SampleFactoryErrorCode;
import com.kant.llm.eval.simple.service.AgentSampleSeedService;
import com.kant.llm.eval.simple.support.FactoryJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentSampleSeedServiceImpl
        extends ServiceImpl<AgentSampleSeedMapper, AgentSampleSeedDO>
        implements AgentSampleSeedService {

    @Override
    public List<SimpleSeedFactoryResponses.Seed> seeds() {
        return list(new LambdaQueryWrapper<AgentSampleSeedDO>()
                .orderByDesc(AgentSampleSeedDO::getId)
                .last("LIMIT 100"))
                .stream()
                .map(s ->
                        new SimpleSeedFactoryResponses.Seed(s.getSeedId(), 1, s.getName(), s.getContentHash(), s.getSourceRef(), FactoryJsonSupport.instant(s.getCreateTime())))
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SimpleSeedFactoryResponses.Seed createSeed(SimpleSeedFactoryRequests.Seed request) {
        // 种子是专家输入，不是模型生成结果；创建种子本身不会进入Worker或消耗模型额度。
        String hash = FactoryJsonSupport.hash(request);
        var old = getOne(new LambdaQueryWrapper<AgentSampleSeedDO>()
                .eq(AgentSampleSeedDO::getIdempotencyKey, request.idempotencyKey()));

        if (old != null) {
            same(hash, old.getRequestHash());
            return seedResponse(old);
        }
        var row = new AgentSampleSeedDO();
        row.setSeedId(id());
        row.setIdempotencyKey(request.idempotencyKey());
        row.setRequestHash(hash);
        row.setName(request.name());
        row.setContent(request.content());
        row.setContentHash(FactoryJsonSupport.digest(request.content()));
        row.setSourceRef(request.sourceRef());
        row.setCreateTime(now());
        row.setUpdateTime(now());
        row.setCreator("system");
        row.setUpdater("system");
        save(row);
        return seedResponse(row);
    }

    /** 校验主体及原文摘要后单独返回种子正文；调用方应将正文作为不可信纯文本展示。 */

    @Override
    public SimpleSeedFactoryResponses.Content seedContent(String seedId) {
        // 正文读取再次校验摘要；返回后仍应作为不可信文本展示，不能直接拼接为HTML。
        var seed = getOne(new LambdaQueryWrapper<AgentSampleSeedDO>()
                .eq(AgentSampleSeedDO::getSeedId, seedId));
        return new SimpleSeedFactoryResponses.Content(seedId, seed.getContent(), seed.getContentHash());
    }

    /** 生成带业务前缀的UUID标识；不是数据库自增主键或分页游标。 */
    private String id() {
        return String.format("seed_%s", UUID.randomUUID().toString().replace("-", ""));
    }

    /** 幂等键只允许重放相同请求，不能携带新参数覆盖首次事实。 */
    private void same(String expected, String actual) {
        if (!expected.equals(actual)) throw error(SampleFactoryErrorCode.IDEMPOTENCY_CONFLICT);
    }

    /** 统一构造D05安全错误，不附带SQL、种子或模型原文。 */
    private ClientException error(SampleFactoryErrorCode code) {
        return new ClientException(code.message(), code);
    }

    /** 使用UTC且截断为微秒，匹配MySQL DATETIME(6)存储精度。 */
    private LocalDateTime now() {
        return FactoryJsonSupport.utc(Instant.now());
    }

    /** 构造不可变版本1的种子索引，避免普通列表暴露正文。 */
    private SimpleSeedFactoryResponses.Seed seedResponse(AgentSampleSeedDO s) {
        return new SimpleSeedFactoryResponses.Seed(s.getSeedId(),
                1,
                s.getName(),
                s.getContentHash(),
                s.getSourceRef(),
                FactoryJsonSupport.instant(s.getCreateTime()));
    }

}
