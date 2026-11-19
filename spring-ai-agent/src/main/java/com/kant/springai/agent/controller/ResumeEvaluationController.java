package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.ResumeEvaluationAgent;
import com.kant.springai.agent.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/spring-ai-agent")
@RestController
@RequiredArgsConstructor
public class ResumeEvaluationController {
    private final ResumeEvaluationAgent resumeEvaluationAgent;

    private final DocumentParseService documentParseService;

    /**
     * 解析上传的 PDF/Word 简历，并生成 HR 筛选报告
     * @param file 简历附件
     * @return HR 筛选报告
     */
    @PostMapping(value = "/resumeEvaluation",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String analyze(@RequestParam("file") MultipartFile file) {
        String resumeContent = documentParseService.extractContent(file);
        return resumeEvaluationAgent.evaluation(resumeContent);
    }
}
