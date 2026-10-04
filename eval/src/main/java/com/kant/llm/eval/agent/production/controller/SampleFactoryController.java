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
 */
@RestController
@RequestMapping("/agent/sample-factory")
public class SampleFactoryController {
    private final SampleProductionService production;
    private final SampleRunRecordService records;
    private final CandidateCaseService candidates;
    private final AssetReviewService review;
    private final ProbePrincipalProvider principals;

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

    @GetMapping("/seeds/{seedId}")
    public Result<FactoryResponses.Seed> seed(@PathVariable String seedId) {
        return Results.success(safely(() -> production.seed(seedId)));
    }

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
     */
    @PostMapping("/tasks/{taskId}/reset")
    public Result<FactoryResponses.Task> resetTask(@PathVariable String taskId,
                                                   @Valid @RequestBody FactoryRequests.ResetTask request) {
        return Results.success(safely(() -> production.resetTask(taskId, request)));
    }

    @GetMapping("/tasks")
    public Result<List<FactoryResponses.Task>> tasks() {
        return Results.success(safely(production::tasks));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<FactoryResponses.Task> task(@PathVariable String taskId) {
        return Results.success(safely(() -> production.task(taskId)));
    }

    /** 人工手动生产或精确槽位补跑；返回受理快照，使用GET读取当前批次进度。 */
    @PostMapping("/tasks/{taskId}/runs")
    public Result<FactoryResponses.Run> start(@PathVariable String taskId, @Valid @RequestBody FactoryRequests.Run request) {
        return Results.success(safely(() -> production.start(taskId, request)));
    }

    @GetMapping("/tasks/{taskId}/runs")
    public Result<List<FactoryResponses.Run>> runs(@PathVariable String taskId) {
        return Results.success(safely(() -> production.runs(taskId)));
    }

    @PostMapping("/tasks/{taskId}/schedule-control")
    public Result<FactoryResponses.Control> schedule(@PathVariable String taskId, @Valid @RequestBody FactoryRequests.ScheduleControl request) {
        return Results.success(safely(() -> production.controlSchedule(taskId, request)));
    }

    /** 触发/遗漏审计最多最近100条，保留原UTC槽位；关闭计划不会删除历史。 */
    @GetMapping("/tasks/{taskId}/schedule-fires")
    public Result<List<FactoryResponses.Fire>> fires(@PathVariable String taskId) {
        return Results.success(safely(() -> production.fires(taskId)));
    }

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

    @GetMapping("/runs/{runId}/operations")
    public Result<FactoryResponses.Page<FactoryResponses.Operation>> operations(@PathVariable String runId,
                                                                                @RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> candidates.operations(runId, afterId, limit)));
    }

    @GetMapping("/runs/{runId}/candidates")
    public Result<FactoryResponses.Page<FactoryResponses.Candidate>> candidates(@PathVariable String runId,
                                                                                @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") long afterId,
                                                                                @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> candidates.list(runId, status, afterId, limit)));
    }

    @GetMapping("/candidates/{candidateId}")
    public Result<FactoryResponses.Candidate> candidate(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.detail(candidateId)));
    }

    @GetMapping("/candidates/{candidateId}/content")
    public Result<FactoryResponses.Content> content(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.content(candidateId)));
    }

    @GetMapping("/candidates/{candidateId}/lineage")
    public Result<List<FactoryResponses.Candidate>> lineage(@PathVariable String candidateId) {
        return Results.success(safely(() -> candidates.lineage(candidateId)));
    }

    @GetMapping("/candidates/{candidateId}/reviews")
    public Result<List<FactoryResponses.Review>> reviews(@PathVariable String candidateId) {
        return Results.success(safely(() -> review.reviews(candidateId)));
    }

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

    @GetMapping("/assets/{caseId}/versions/{version}")
    public Result<FactoryResponses.Asset> asset(@PathVariable String caseId, @PathVariable long version) {
        return Results.success(safely(() -> review.asset(caseId, version)));
    }

    @GetMapping("/assets/{caseId}/versions/{version}/content")
    public Result<FactoryResponses.Content> assetContent(@PathVariable String caseId, @PathVariable long version) {
        return Results.success(safely(() -> review.assetContent(caseId, version)));
    }

    @PostMapping("/case-sets")
    public Result<FactoryResponses.CaseSet> createCaseSet(@Valid @RequestBody FactoryRequests.CaseSet request) {
        return Results.success(safely(() -> review.createCaseSet(request)));
    }

    @GetMapping("/case-sets")
    public Result<FactoryResponses.Page<FactoryResponses.CaseSet>> caseSets(@RequestParam(defaultValue = "0") long afterId,
                                                                            @RequestParam(defaultValue = "50") int limit) {
        return Results.success(safely(() -> review.caseSets(afterId, limit)));
    }

    @GetMapping("/case-sets/{caseSetId}/versions/{version}")
    public Result<FactoryResponses.CaseSet> caseSet(@PathVariable String caseSetId, @PathVariable long version) {
        return Results.success(safely(() -> review.caseSet(caseSetId, version)));
    }
}
