package com.kant.llm.eval.controller;

import com.kant.llm.eval.service.RiskSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/risk")
public class RiskSyncController {

    @Autowired
    private RiskSyncService riskSyncService;

    /**
     * 手动同步风险词库
     * @return 同步成功提示信息
     */
    @PostMapping("/sync")
    public String syncRiskVocabulary() {
        log.info("收到手动同步请求");
        riskSyncService.syncAll();
        return "同步成功";
    }
}
