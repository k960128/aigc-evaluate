package com.chinatelecom.aigc.evaluate.web.websocket.handler;

import com.chinatelecom.aigc.evaluate.web.websocket.message.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.EOFException;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    // 支持同一个 userId 绑定多个连接
    private final Map<String, List<WebSocketSession>> userIdToSessionsMap = new ConcurrentHashMap<>();

    // 用于 JSON 序列化
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("WebSocket 连接成功，连接ID: {}", session.getId());
        // 等待客户端发送 userId 绑定
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到客户端消息: 连接ID={} 内容: {}", session.getId(), payload);

        String userId = payload.trim();
        if (userId.isEmpty()) {
            Message error = new Message("error", "userId 不能为空");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            return;
        }

        // 绑定 userId，避免重复添加同一个 session
        userIdToSessionsMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        List<WebSocketSession> sessions = userIdToSessionsMap.get(userId);
        if (!sessions.contains(session)) {
            sessions.add(session);
            log.info("已绑定 userId={} 到连接ID={}", userId, session.getId());
        } else {
            log.info("连接ID={} 已绑定到 userId={}，跳过重复绑定", session.getId(), userId);
        }

        // 给客户端发送确认消息
        Message ack = new Message("user_ack", "已绑定 userId: " + userId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("WebSocket 连接关闭，连接ID: {}, 原因: {}", session.getId(), status);
        removeSession(session);

        // 显式关闭 session，防止资源泄露
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.warn("关闭 WebSocket session 失败，连接ID: {}", session.getId(), e);
        }
    }


    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if (exception instanceof EOFException) {
            // 前端关闭导致的 EOF，不打印日志
            return;
        }

        log.error("WebSocket 连接异常，连接ID: {}, 异常信息: {}", session.getId(), exception.getMessage());
        removeSession(session);

        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            log.warn("关闭 WebSocket session 失败，连接ID: {}", session.getId());
        }
    }


    // 移除指定 WebSocketSession
    private void removeSession(WebSocketSession session) {
        userIdToSessionsMap.forEach((userId, sessions) -> {
            if (sessions.remove(session)) {
                log.info("已从 userId={} 移除连接ID={}", userId, session.getId());
            }
        });

        // 清理空的 userId 映射
        userIdToSessionsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // 通过 userId 发送消息给所有客户端
    public void sendToClient(String userId, Message message) {
        List<WebSocketSession> sessions = userIdToSessionsMap.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("没有找到可用连接，userId={}", userId);
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("消息序列化失败", e);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                    log.info("发送消息给 userId={} 的连接ID={} 内容={}", userId, session.getId(), payload);
                } catch (Exception e) {
                    log.error("发送 WebSocket 消息失败，userId={} 连接ID={}", userId, session.getId(), e);
                }
            } else {
                log.warn("连接已关闭，无法发送消息，userId={} 连接ID={}", userId, session.getId());
            }
        }
    }
}
