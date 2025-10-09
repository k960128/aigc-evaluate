package com.chinatelecom.aigc.evaluate.web.config;

import com.chinatelecom.aigc.evaluate.web.websocket.handler.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;

    public WebSocketConfig(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/aigc/evaluate/report/ws")
                .setAllowedOrigins("*"); // 根据需要设置跨域
    }
}
