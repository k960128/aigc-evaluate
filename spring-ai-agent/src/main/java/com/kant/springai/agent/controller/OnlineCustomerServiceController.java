package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.OnlineCustomerServiceAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/online-customer")
public class OnlineCustomerServiceController {

    private final OnlineCustomerServiceAgent onlineCustomerServiceAgent;

    public OnlineCustomerServiceController(OnlineCustomerServiceAgent onlineCustomerServiceAgent) {
        this.onlineCustomerServiceAgent = onlineCustomerServiceAgent;
    }

    /**
     * 提交用户咨询内容，生成专业客服回复。
     * @param request 用户咨询请求
     * @return 客服回复
     */
    @PostMapping("/agent/onlineCustomerService")
    public String route(@RequestBody OnlineCustomerServiceRequest request) {
        if (request == null || StringUtils.isBlank(request.message())) {
            throw new IllegalArgumentException("咨询内容不能为空");
        }
        return onlineCustomerServiceAgent.route(request.message());
    }

    /**
     * 在线客服请求对象。
     * @param message 用户咨询内容
     */
    public record OnlineCustomerServiceRequest(String message) {
    }
}
