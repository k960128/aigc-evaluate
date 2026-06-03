package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 安全评测 - 风险词库极速拦截字典 (L1) 管理接口
 */
@RestController
@RequestMapping("risk/vocabularies")
@RequiredArgsConstructor
public class RiskVocabularyController {
    private final RiskVocabularyKeywordService keywordService;

    /**
     * 分页查询特征词
     */
    @GetMapping("/page")
    public Result<Page<RiskVocabularyKeywordDO>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<RiskVocabularyKeywordDO> queryWrapper = new LambdaQueryWrapper<>();
        if (groupId != null) {
            queryWrapper.eq(RiskVocabularyKeywordDO::getGroupId, groupId);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(RiskVocabularyKeywordDO::getKeyword, keyword);
        }
        queryWrapper.orderByDesc(RiskVocabularyKeywordDO::getCreateTime);

        return Results.success(keywordService.page(new Page<>(current, size), queryWrapper));
    }

    /**
     * 录入新的特征词 (保存后 syncStatus 默认为 0 待同步)
     */
    @PostMapping
    public Result<Boolean> save(@RequestBody RiskVocabularyKeywordDO keywordDO) {
        keywordDO.setSyncStatus(0); // 0: 待同步 (Pending)
        return Results.success(keywordService.save(keywordDO));
    }

    /**
     * 模拟推送到 Redis 并更新状态
     * 实际业务中这里会调用 RedisTemplate 发布消息，并构建 AC 树
     */
    @PostMapping("/sync-to-redis")
    public Result<String> syncToRedis() {
        // 1. 查出所有待同步的数据 (sync_status = 0)
        LambdaQueryWrapper<RiskVocabularyKeywordDO> query = new LambdaQueryWrapper<>();
        query.eq(RiskVocabularyKeywordDO::getSyncStatus, 0);
        List<RiskVocabularyKeywordDO> pendingList = keywordService.list(query);

        if (pendingList.isEmpty()) {
            return Results.success("当前没有需要同步的词条");
        }

        // 2. 模拟推送逻辑 (后续在阶段二实现真实的 Redis Pub/Sub)
        // ...

        // 3. 将状态全部更新为 1 (已同步)
        for (RiskVocabularyKeywordDO kw : pendingList) {
            kw.setSyncStatus(1);
        }
        keywordService.updateBatchById(pendingList);

        return Results.success("成功推送 " + pendingList.size() + " 条特征词，AC自动机将在一分钟内完成热更");
    }

    /**
     * 删除特征词
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Results.success(keywordService.removeById(id));
    }
}
