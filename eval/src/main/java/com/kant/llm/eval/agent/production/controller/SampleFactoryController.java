package com.kant.llm.eval.agent.production.controller;

import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.production.dto.FactoryRequests;
import com.kant.llm.eval.agent.production.dto.FactoryResponses;
import com.kant.llm.eval.agent.production.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.kant.llm.eval.agent.production.support.SampleFactoryApiSupport.safely;

/**
 * D05独立样本工厂入口。HTTP只受理控制命令，不同步等待生成，不接受targetId或身份覆盖。
 * 前端轮询批次/候选即可；样本正文使用独立认证接口，按纯文本展示。
 * <p>所有接口返回Result外壳，接口成功仅代表请求处理成功，不等于批次成功或质量通过。
 * afterId是主键游标而非页码；version分别属于计划、批次、候选或正式资产，不能互换。</p>
 */
@RestController
@RequestMapping("/agent/sample-factory")
public class SampleFactoryController {
    /** 种子、计划、重置、批次受理与调度控制。 */
    private final SampleProductionService production;
    /** 批次身份校验及停止控制。 */
    private final SampleRunRecordService records;
    /** 候选、操作事实和改写血缘的只读查询。 */
    private final CandidateCaseService candidates;
    /** 人工审核、正式资产和固定用例集。 */
    private final AssetReviewService review;
    /** 当前认证主体，身份只由服务端提供。 */
    private final ProbePrincipalProvider principals;

    /** 注入控制面服务；Controller不直接写Mapper或调用生成模型。 */
    public SampleFactoryController(SampleProductionService production, SampleRunRecordService records,
                                   CandidateCaseService candidates, AssetReviewService review, ProbePrincipalProvider principals) {
        this.production = production;
        this.records = records;
        this.candidates = candidates;
        this.review = review;
        this.principals = principals;
    }

    /** 部署配置的生成角色快照；available不代表模型健康探测已经成功。 */
    @GetMapping("/generator-profile")
    public Result<SampleGenerationPort.Profile> profile() {
        return Results.success(safely(production::profile));
    }

    /** 创建不可变专家种子；没有调用模型。 */
    @PostMapping("/seeds")
    public Result<FactoryResponses.Seed> createSeed(@Valid @RequestBody FactoryRequests.Seed request) {
        return Results.success(safely(() -> production.createSeed(request)));
    }

    /** 首版种子/计划导航列表最多返回最近100条；大量候选另用游标分页。 */
    @GetMapping("/seeds")
    public Result<List<FactoryResponses.Seed>> seeds() {
        return Results.success(safely(production::seeds));
    }

    /** 读取当前主体的种子索引，正文不混入元数据响应。 */
    @GetMapping("/seeds/{seedId}")
    public Result<FactoryResponses.Seed> seed(@PathVariable String seedId) {
        return Results.success(safely(() -> production.seed(seedId)));
    }

    /** 通过独立认证入口读取种子正文，并核对原文摘要。 */
    @GetMapping("/seeds/{seedId}/content")
    public Result<FactoryResponses.Content> seedContent(@PathVariable String seedId) {
        return Results.success(safely(() -> production.seedContent(seedId)));
    }

    /** 冻结种子、配置、单批预算和可选计划；scheduleEnabled仍为false。 */
    @PostMapping("/tasks")
    public Result<FactoryResponses.Task> createTask(@Valid @RequestBody FactoryRequests.Task request) {
        return Results.success(safely(() -> production.createTask(request)));
    }

    /**
     * 基于旧计划创建新的配置快照；不修改旧计划，不自动启用新计划的定时任务。
     * 返回新的taskId，预算仍沿用旧值；该操作不创建批次，也不取消旧计划的调度。
     */
    @PostMapping("/tasks/{taskId}/reset")
    public Result<FactoryResponses.Task> resetTask(@PathVariable String taskId,
                                                   @Valid @RequestBody FactoryRequests.ResetTask request) {
        return Results.success(safely(() -> production.resetTask(taskId, request)));
    }

    /** 返回当前主体最近100条生产计划，不刷新计划中的角色快照。 */
    @GetMapping("/tasks")
    public Result<List<FactoryResponses.Task>> tasks() {
        return Results.success(safely(production::tasks));
    }

    /** 查询冻结生成配置及当前计划控制版本，执行和启停请求使用此版本。 */
    @GetMapping("/tasks/{taskId}")
    public Result<FactoryResponses.Task> task(@PathVariable String taskId) {
        return Results.success(safely(() -> production.task(taskId)));
    }

    /** 人工手动生产或精确槽位补跑；返回受理快照，使用GET读取当前批次进度。 */
    @PostMapping("/tasks/{taskId}/runs")
    public Result<FactoryResponses.Run> start(@PathVariable String taskId, @Valid @RequestBody FactoryRequests.Run request) {
        return Results.success(safely(() -> production.start(taskId, request)));
    }

    /** 返回计划最近100个批次的当前状态，失败历史不会被重新执行覆盖。 */
    @GetMapping("/tasks/{taskId}/runs")
    public Result<List<FactoryResponses.Run>> runs(@PathVariable String taskId) {
        return Results.success(safely(() -> production.runs(taskId)));
    }

    /** 显式启停每日生产；关闭不停止既有批次，启用不重置原调度游标。 */
    @PostMapping("/tasks/{taskId}/schedule-control")
    public Result<FactoryResponses.Control> schedule(@PathVariable String taskId, @Valid @RequestBody FactoryRequests.ScheduleControl request) {
        return Results.success(safely(() -> production.controlSchedule(taskId, request)));
    }

    /** 触发/遗漏审计最多最近100条，保留原UTC槽位；关闭计划不会删除历史。 */
    @GetMapping("/tasks/{taskId}/schedule-fires")
    public Result<List<FactoryResponses.Fire>> fires(@PathVariable String taskId) {
        return Results.success(safely(() -> production.fires(taskId)));
    }

    /** 查询当前批次进度与账本；前端轮询此接口，不复用创建批次时的首次受理快照。 */
    @GetMapping("/runs/{runId}")
    public Result<FactoryResponses.Run> run(@PathVariable String runId) {
        return Results.success(safely(() -> {
            var p = principals.currentPrincipal();
            return production.runResponse(records.authorized(p.tenantId(), p.actorId(), runId));
        }));
    }

    /** 仅停止新增调用；已发出的操作需等待确认事实，不承诺立即取消供应商请求。 */
    @PostMapping("/runs/{runId}/stop")
    public Result<FactoryResponses.Control> stop(@PathVariable String runId, @Valid @RequestBody FactoryRequests.Stop request) {
        return Results.success(safely(() -> {
            var p = principals.currentPrincipal();
            return records.stop(p.tenantId(), p.actorId(), runId, request);
        }));
    }

    /** 分页查询物理操作分类与用量，不返回提示词、种子或原始模型响应。 */
    @GetMapping("/runs/{runId}/operations")
    public Result<FactoryResponses.Page<FactoryResponses.Operation>> operations(@PathVariable String runId,
                                                                                @RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> candidates.operations(runId, afterId, limit)));
    }

    /** 按可选候选状态分页查询；afterId非负、limit为1至100，审核后刷新待审列表从0开始。 */
    @GetMapping("/runs/{runId}/candidates")
    public Result<FactoryResponses.Page<FactoryResponses.Candidate>> candidates(@PathVariable String runId,
                                                                                @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") long afterId,
                                                                                @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> candidates.list(runId, status, afterId, limit)));
    }

    /** 查询候选索引，分别展示生成状态、质量报告和人工准入状态。 */
    @GetMapping("/candidates/{candidateId}")
    public Result<FactoryResponses.Candidate> candidate(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.detail(candidateId)));
    }

    /** 返回受限原文；拒绝或非法生成时正文可能为null，前端不能当作HTML执行。 */
    @GetMapping("/candidates/{candidateId}/content")
    public Result<FactoryResponses.Content> content(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.content(candidateId)));
    }

    /** 返回根候选到当前候选的改写链，最多原生成与两次改写，不覆盖父候选。 */
    @GetMapping("/candidates/{candidateId}/lineage")
    public Result<List<FactoryResponses.Candidate>> lineage(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.lineage(candidateId)));
    }

    /** 读取人工审核历史，不把意见当成目标安全结论。 */
    @GetMapping("/candidates/{candidateId}/reviews")
    public Result<List<FactoryResponses.Review>> reviews(@PathVariable String candidateId) {
        return Results.success(safely(() -> review.reviews(candidateId)));
    }

    /** 批准或退回待审核候选；批准原子生成正式资产，退回不自动触发改写，也不恢复原批次。 */
    @PostMapping("/candidates/{candidateId}/reviews")
    public Result<FactoryResponses.Review> review(@PathVariable String candidateId, @Valid @RequestBody FactoryRequests.Review request) {
        return Results.success(safely(() -> review.review(candidateId, request)));
    }

    /** 正式资产列表只有已审核通过的用例，候选不可直接放入用例集。 */
    @GetMapping("/assets")
    public Result<FactoryResponses.Page<FactoryResponses.Asset>> assets(@RequestParam(defaultValue = "0") long afterId,
                                                                        @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> review.assets(afterId, limit)));
    }

    /** 查询正式用例精确版本元数据，不接受候选ID或latest别名。 */
    @GetMapping("/assets/{caseId}/versions/{version}")
    public Result<FactoryResponses.Asset> asset(@PathVariable String caseId, @PathVariable long version) {
        return Results.success(safely(() -> review.asset(caseId, version)));
    }

    /** 通过正式资产引用读取候选原文，并核对正式版本冻结的摘要。 */
    @GetMapping("/assets/{caseId}/versions/{version}/content")
    public Result<FactoryResponses.Content> assetContent(@PathVariable String caseId, @PathVariable long version) {
        return Results.success(safely(() -> review.assetContent(caseId, version)));
    }

    /** 将已批准资产的精确版本组成固定集合，不自动启动评测或写入旧dataset表。 */
    @PostMapping("/case-sets")
    public Result<FactoryResponses.CaseSet> createCaseSet(@Valid @RequestBody FactoryRequests.CaseSet request) {
        return Results.success(safely(() -> review.createCaseSet(request)));
    }

    /** 游标分页查询当前主体的正式集合，内部保持人工选择的用例顺序。 */
    @GetMapping("/case-sets")
    public Result<FactoryResponses.Page<FactoryResponses.CaseSet>> caseSets(@RequestParam(defaultValue = "0") long afterId,
                                                                            @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> review.caseSets(afterId, limit)));
    }

    /** 读取指定集合精确版本；集合引用不会自动替换成其他用例版本。 */
    @GetMapping("/case-sets/{caseSetId}/versions/{version}")
    public Result<FactoryResponses.CaseSet> caseSet(@PathVariable String caseSetId, @PathVariable long version) {
        return Results.success(safely(() -> review.caseSet(caseSetId, version)));
    }
}
