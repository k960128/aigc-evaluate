package com.kant.llm.eval.agent.finding.controller;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.finding.dto.*;
import com.kant.llm.eval.agent.finding.service.FindingService;
import com.kant.llm.eval.agent.finding.service.FindingLifecycleService;
import static com.kant.llm.eval.agent.validation.support.D04ApiSupport.safely;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 风险发现入口；复核、人工确认、归并拆分与整改命令分别留痕。 */
@RestController
@RequestMapping("/agent/findings")
public class FindingController {
    private final FindingService service;
    private final FindingLifecycleService lifecycle;
    public FindingController(FindingService service,FindingLifecycleService lifecycle) { this.service=service; this.lifecycle=lifecycle; }
    /** 从运行疑似发现列表取得findingId，查看原发生和当前确认状态。 */
    @GetMapping("/{findingId}")
    public Result<FindingDetailResponse> detail(@PathVariable String findingId) { return Results.success(service.detail(findingId)); }
    /** 查询复核历史，分页游标为上一页末条acceptedVersion。 */
    @GetMapping("/{findingId}/reviews")
    public Result<List<FindingReviewResponse>> history(@PathVariable String findingId,@RequestParam(defaultValue="0") long afterVersion) {
        return Results.success(service.history(findingId,afterVersion));
    }
    /** 自动证据核对，不调用目标，不自动确认风险。 */
    @PostMapping("/{findingId}/evidence-reviews")
    public Result<FindingReviewResponse> checkEvidence(@PathVariable String findingId,@Valid @RequestBody FindingEvidenceReviewRequest request) {
        return Results.success(service.checkEvidence(findingId,request));
    }
    /** 追加人工意见，确认/驳回/待定与只留意见分别处理。 */
    @PostMapping("/{findingId}/reviews")
    public Result<FindingReviewResponse> review(@PathVariable String findingId,@Valid @RequestBody FindingHumanReviewRequest request) {
        return Results.success(service.review(findingId,request));
    }
    /** 归并/拆分、受理整改、确认修复、重新打开和接受风险，不覆盖复核意见。 */
    @PostMapping("/{findingId}/changes")
    public Result<FindingChangeResponse> change(@PathVariable String findingId,@Valid @RequestBody FindingLifecycleRequest request) {
        return Results.success(safely(() -> lifecycle.change(findingId,request)));
    }
    /** 变更历史游标为上一页末条acceptedVersion，与复核分页分别查询。 */
    @GetMapping("/{findingId}/changes")
    public Result<List<FindingChangeResponse>> changes(@PathVariable String findingId,@RequestParam(defaultValue="0") long afterVersion) {
        return Results.success(safely(() -> lifecycle.history(findingId,afterVersion)));
    }
}
