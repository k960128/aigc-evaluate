package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dto.req.L2KbSyncEventPageRequest;
import com.kant.llm.eval.dto.req.L2RiskAttackFeaturePageRequest;
import com.kant.llm.eval.dto.req.L2RiskDetailRulePageRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskAttackFeatureRequest;
import com.kant.llm.eval.dto.req.SaveL2RiskDetailRuleRequest;
import com.kant.llm.eval.dto.req.UpdateL2KbSyncEventStatusRequest;
import com.kant.llm.eval.dto.req.UpdateL2KnowledgeStatusRequest;
import com.kant.llm.eval.dto.resp.L2KbSyncEventVO;
import com.kant.llm.eval.dto.resp.L2RiskAttackFeatureVO;
import com.kant.llm.eval.dto.resp.L2RiskDetailRuleVO;
import com.kant.llm.eval.service.L2KnowledgeBaseService;
import com.kant.llm.eval.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L2 知识库管理接口。
 *
 * <p>提供风险小类判定规则、攻击特征和索引同步事件的维护能力。
 * MySQL 仍是唯一事实源，ES/Milvus 后续只消费同步事件。</p>
 *
 * <p>本控制器只负责 HTTP 入参承接和统一响应包装，不在这里直接拼接查询条件或写同步事件。
 * 这样规则维护、特征维护和同步状态回写都收敛在 {@link L2KnowledgeBaseService} 的事务边界内，
 * 避免后续真实索引接入时出现“数据库已改、同步事件漏写”的不一致问题。</p>
 */
@RestController
@RequestMapping("/l2/knowledge-base")
@RequiredArgsConstructor
public class L2KnowledgeBaseController {

    private final L2KnowledgeBaseService l2KnowledgeBaseService;

    /**
     * 分页查询风险小类判定规则。
     *
     * <p>主要用于后台列表页查看每个 risk_details 是否已经维护 L2 判定边界。
     * 支持按风险大类、小类、严重等级和启停状态过滤。</p>
     */
    @PostMapping("/detail-rule/page")
    public Result<Page<L2RiskDetailRuleVO>> pageDetailRule(@RequestBody L2RiskDetailRulePageRequest request) {
        return Results.success(l2KnowledgeBaseService.pageDetailRule(request));
    }

    /**
     * 查询风险小类判定规则详情。
     *
     * <p>返回规则正文、判定边界、正反例和关联的大类/小类名称，供编辑页回显。</p>
     */
    @GetMapping("/detail-rule/get")
    public Result<L2RiskDetailRuleVO> getDetailRule(@RequestParam("id") Long id) {
        return Results.success(l2KnowledgeBaseService.getDetailRule(id));
    }

    /**
     * 创建风险小类判定规则。
     *
     * <p>创建成功后会同步写入一条 DETAIL_RULE 类型的 kb_sync_event，
     * 方便后续真实 ES/Milvus 索引器感知规则变化。</p>
     */
    @PostMapping("/detail-rule/create")
    public Result<L2RiskDetailRuleVO> createDetailRule(@RequestBody SaveL2RiskDetailRuleRequest request) {
        return Results.success(l2KnowledgeBaseService.createDetailRule(request));
    }

    /**
     * 更新风险小类判定规则。
     *
     * <p>更新规则会增加规则版本号，并写入 UPDATE 同步事件。
     * 当前阶段规则不直接参与 Mock 召回，但会参与 L2 日志和风险小类说明补全。</p>
     */
    @PutMapping("/detail-rule/update")
    public Result<L2RiskDetailRuleVO> updateDetailRule(@RequestBody SaveL2RiskDetailRuleRequest request) {
        return Results.success(l2KnowledgeBaseService.updateDetailRule(request));
    }

    /**
     * 启用或禁用风险小类判定规则。
     *
     * <p>启停属于知识库版本变化，因此使用 REINDEX 事件表达“需要重新同步当前聚合”。</p>
     */
    @PutMapping("/detail-rule/status")
    public Result<Boolean> updateDetailRuleStatus(@RequestBody UpdateL2KnowledgeStatusRequest request) {
        requireNonNull(request, "规则状态更新请求不能为空");
        return Results.success(l2KnowledgeBaseService.updateDetailRuleStatus(
                request.getId(), request.getStatus(), request.getUpdater()));
    }

    /**
     * 删除风险小类判定规则。
     *
     * <p>删除为逻辑删除，由 MyBatis Plus 处理 deleted 字段；同时写入 DELETE 同步事件。</p>
     */
    @DeleteMapping("/detail-rule/delete")
    public Result<Boolean> deleteDetailRule(@RequestParam("id") Long id) {
        return Results.success(l2KnowledgeBaseService.deleteDetailRule(id));
    }

    /**
     * 分页查询攻击特征。
     *
     * <p>攻击特征是 L2 召回的主要知识来源。
     * 列表支持按风险层级、特征类型、极性和同步状态过滤，便于排查“为什么某条特征没有参与召回”。</p>
     */
    @PostMapping("/attack-feature/page")
    public Result<Page<L2RiskAttackFeatureVO>> pageAttackFeature(@RequestBody L2RiskAttackFeaturePageRequest request) {
        return Results.success(l2KnowledgeBaseService.pageAttackFeature(request));
    }

    /**
     * 查询攻击特征详情。
     *
     * <p>详情包含 contentHash、版本号以及 ES/Milvus 分别的同步状态，
     * 用于后续定位单条特征在索引侧的同步进度。</p>
     */
    @GetMapping("/attack-feature/get")
    public Result<L2RiskAttackFeatureVO> getAttackFeature(@RequestParam("id") Long id) {
        return Results.success(l2KnowledgeBaseService.getAttackFeature(id));
    }

    /**
     * 创建攻击特征。
     *
     * <p>创建成功后会计算 contentHash、将同步状态置为待同步，并写入 ATTACK_FEATURE/CREATE 事件。
     * MySQL Mock 召回开启时，新特征会直接参与后续 L2 验证。</p>
     */
    @PostMapping("/attack-feature/create")
    public Result<L2RiskAttackFeatureVO> createAttackFeature(@RequestBody SaveL2RiskAttackFeatureRequest request) {
        return Results.success(l2KnowledgeBaseService.createAttackFeature(request));
    }

    /**
     * 更新攻击特征。
     *
     * <p>更新会重新计算 contentHash 并将综合/ES/Milvus 同步状态全部置回待同步。
     * 这是为了提醒后续索引器重新写入该特征，避免 MySQL 与索引数据漂移。</p>
     */
    @PutMapping("/attack-feature/update")
    public Result<L2RiskAttackFeatureVO> updateAttackFeature(@RequestBody SaveL2RiskAttackFeatureRequest request) {
        return Results.success(l2KnowledgeBaseService.updateAttackFeature(request));
    }

    /**
     * 启用或禁用攻击特征。
     *
     * <p>启停会影响 L2 召回可见性，因此同样会生成 REINDEX 同步事件，
     * 后续真实索引接入后可据此更新索引中的状态字段或删除不可见文档。</p>
     */
    @PutMapping("/attack-feature/status")
    public Result<Boolean> updateAttackFeatureStatus(@RequestBody UpdateL2KnowledgeStatusRequest request) {
        requireNonNull(request, "攻击特征状态更新请求不能为空");
        return Results.success(l2KnowledgeBaseService.updateAttackFeatureStatus(
                request.getId(), request.getStatus(), request.getUpdater()));
    }

    /**
     * 删除攻击特征。
     *
     * <p>删除前会先把特征标记为 DELETE_PENDING 并写入 DELETE 事件，再执行逻辑删除。
     * 这样后续真实索引器仍可以根据事件快照删除 ES/Milvus 中的旧数据。</p>
     */
    @DeleteMapping("/attack-feature/delete")
    public Result<Boolean> deleteAttackFeature(@RequestParam("id") Long id) {
        return Results.success(l2KnowledgeBaseService.deleteAttackFeature(id));
    }

    /**
     * 分页查询知识库同步事件。
     *
     * <p>用于观察知识库变更是否已经被 ES/Milvus 消费。
     * 当前阶段没有真实索引器，仍可以通过该接口人工查看本地事件闭环。</p>
     */
    @PostMapping("/sync-event/page")
    public Result<Page<L2KbSyncEventVO>> pageSyncEvent(@RequestBody L2KbSyncEventPageRequest request) {
        return Results.success(l2KnowledgeBaseService.pageSyncEvent(request));
    }

    /**
     * 更新知识库同步事件状态，并回写攻击特征同步状态。
     *
     * <p>该接口是本阶段的“同步结果入口”：真实索引器后续完成 ES/Milvus 写入后，
     * 可以调用它回填成功/失败状态；开发阶段也可以手动调用它验证状态流转。</p>
     */
    @PutMapping("/sync-event/status")
    public Result<Boolean> updateSyncEventStatus(@RequestBody UpdateL2KbSyncEventStatusRequest request) {
        return Results.success(l2KnowledgeBaseService.updateSyncEventStatus(request));
    }

    /**
     * 轻量空值校验。
     *
     * <p>这里只校验 Controller 层必须存在的请求对象，字段级业务校验统一放在 Service 内，
     * 避免同一套规则在多个入口重复维护。</p>
     */
    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new ServiceException(message);
        }
    }
}
