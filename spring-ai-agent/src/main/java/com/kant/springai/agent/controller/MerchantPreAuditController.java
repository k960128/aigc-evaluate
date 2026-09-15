package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.MerchantPreAuditAgent;
import com.kant.springai.agent.service.DocumentParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/merchant/pre-audit")
public class MerchantPreAuditController {

    private final DocumentParseService documentParseService;
    private final MerchantPreAuditAgent merchantPreAuditAgent;


    public MerchantPreAuditController(MerchantPreAuditAgent merchantPreAuditAgent, DocumentParseService documentParseService) {
        this.merchantPreAuditAgent = merchantPreAuditAgent;
        this.documentParseService = documentParseService;
    }

    /**
     * 提交商户入网申请资料，生成商户接入智能预审报告。
     * @param files 商户接入预审资料
     * @return 商户接入智能预审报告
     */
    @PostMapping("/agent/merchantPreAudit")
    public String preAudit(@RequestParam("files") List<MultipartFile> files) {
        // 拼接所有文件内容
        String content = files.stream()
                .filter(file -> !file.isEmpty()) // 过滤空文件
                .map(documentParseService::extractContent) // 解析文件内容
                .collect(Collectors.joining("\n\n")); // 拼接文件内容
        return merchantPreAuditAgent.process(content);
    }
}
