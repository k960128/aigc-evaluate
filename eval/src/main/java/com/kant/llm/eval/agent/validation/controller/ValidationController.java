package com.kant.llm.eval.agent.validation.controller;

import com.kant.agent.contract.validation.ValidationPlan;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.validation.dto.*;
import com.kant.llm.eval.agent.validation.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.kant.llm.eval.agent.validation.support.D04ApiSupport.safely;

/** 固定用例资产与独立验证API；所有写入均受认证归属、幂等键和版本约束。 */
@RestController
@RequestMapping("/agent")
public class ValidationController {
    private final RegressionCaseService cases;
    private final ReplayService replays;

    public ValidationController(RegressionCaseService cases, ReplayService replays) {
        this.cases = cases;
        this.replays = replays;
    }

    /** 生成候选只冻结输入引用，不调用目标。 */
    @PostMapping("/findings/{findingId}/case-candidates")
    public Result<ValidationResponses.Candidate> createCandidate(@PathVariable String findingId, @Valid @RequestBody ValidationRequests.Candidate request) {
        return Results.success(safely(() -> cases.create(findingId, request)));
    }

    /** 查询该发现的候选列表及审核状态。 */
    @GetMapping("/findings/{findingId}/case-candidates")
    public Result<List<ValidationResponses.Candidate>> candidates(@PathVariable String findingId) {
        return Results.success(safely(() -> cases.candidates(findingId)));
    }

    @GetMapping("/case-candidates/{candidateId}")
    public Result<ValidationResponses.Candidate> candidate(@PathVariable String candidateId) {
        return Results.success(safely(() -> cases.candidate(candidateId)));
    }

    /** RETURN保留历史，修改后创建新候选；APPROVE追加正式版本。 */
    @PostMapping("/case-candidates/{candidateId}/reviews")
    public Result<ValidationResponses.CaseReview> review(@PathVariable String candidateId, @Valid @RequestBody ValidationRequests.CaseReview request) {
        return Results.success(safely(() -> cases.review(candidateId, request)));
    }

    @GetMapping("/case-candidates/{candidateId}/reviews")
    public Result<List<ValidationResponses.CaseReview>> reviews(@PathVariable String candidateId) {
        return Results.success(safely(() -> cases.reviews(candidateId)));
    }

    /** 必须指定正式版本，不能拿未审核候选执行固定回归。 */
    @GetMapping("/regression-cases/{caseId}/versions/{version}")
    public Result<ValidationResponses.FormalCase> formal(@PathVariable String caseId, @PathVariable long version) {
        return Results.success(safely(() -> cases.formal(caseId, version)));
    }

    @GetMapping("/findings/{findingId}/regression-cases")
    public Result<List<ValidationResponses.FormalCase>> formalCases(@PathVariable String findingId) {
        return Results.success(safely(() -> cases.formalCases(findingId)));
    }

    @GetMapping("/regression-cases/{caseId}/versions")
    public Result<List<ValidationResponses.FormalCase>> versions(@PathVariable String caseId) {
        return Results.success(safely(() -> cases.versions(caseId)));
    }

    /** 创建原快照下的新物理执行，使用新干净会话和独立预算。 */
    @PostMapping("/findings/{findingId}/replays")
    public Result<ValidationResponses.Run> replay(@PathVariable String findingId, @Valid @RequestBody ValidationRequests.Run request) {
        return Results.success(safely(() -> replays.create(findingId, ValidationPlan.Kind.REPLAY, request)));
    }

    /** 新快照下执行全部当前正式用例，不生成或变异输入。 */
    @PostMapping("/findings/{findingId}/regression-runs")
    public Result<ValidationResponses.Run> regression(@PathVariable String findingId, @Valid @RequestBody ValidationRequests.Run request) {
        return Results.success(safely(() -> replays.create(findingId, ValidationPlan.Kind.REGRESSION, request)));
    }

    @GetMapping("/findings/{findingId}/validation-runs")
    public Result<List<ValidationResponses.Run>> history(@PathVariable String findingId) {
        return Results.success(safely(() -> replays.history(findingId)));
    }

    /** 当前使用轮询查询，不冒充D03探索SSE事件。 */
    @GetMapping("/validation-runs/{runId}")
    public Result<ValidationResponses.Run> run(@PathVariable String runId) {
        return Results.success(safely(() -> replays.detail(runId)));
    }

    /** 新命中与原始发生分开展示，runId/assessmentId可追到真实证据。 */
    @GetMapping("/findings/{findingId}/validation-occurrences")
    public Result<List<ValidationResponses.Occurrence>> occurrences(@PathVariable String findingId) {
        return Results.success(safely(() -> replays.occurrences(findingId)));
    }

    @GetMapping("/validation-runs/{runId}/assessments")
    public Result<List<com.kant.agent.contract.assessment.Assessment>> assessments(@PathVariable String runId) {
        return Results.success(safely(() -> replays.assessments(runId)));
    }

    @PostMapping("/validation-runs/{runId}/stop")
    public Result<ValidationResponses.Stop> stop(@PathVariable String runId, @Valid @RequestBody ValidationRequests.Stop request) {
        return Results.success(safely(() -> replays.stop(runId, request)));
    }
}
