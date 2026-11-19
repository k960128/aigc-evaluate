package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.CodeReviewAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/code-review")
public class CodeReviewController {

    private final CodeReviewAgent codeReviewAgent;

    public CodeReviewController(CodeReviewAgent codeReviewAgent) {
        this.codeReviewAgent = codeReviewAgent;
    }

    /**
     * 提交 Java 代码，生成代码质量审查报告。
     * @param request 代码审查请求
     * @return 代码质量审查报告
     */
    @PostMapping("/agent/codeReview")
    public String review(@RequestBody CodeReviewRequest request) {
        if (request == null || StringUtils.isBlank(request.codeContent())) {
            throw new IllegalArgumentException("代码内容不能为空");
        }
        return codeReviewAgent.review(request.codeContent());
    }

    /**
     * 代码审查请求对象。
     * @param codeContent 待审查的 Java 代码
     */
    public record CodeReviewRequest(String codeContent) {
    }
}
