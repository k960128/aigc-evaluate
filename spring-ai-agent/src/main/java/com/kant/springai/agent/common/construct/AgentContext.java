package com.kant.springai.agent.common.construct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 黑板（Blackboard）：Agent 之间的共享上下文。
 * 每个 Agent 从里面读输入、往里面写产出，彼此解耦。
 */
public class AgentContext {

    private final String task;
    private final Map<String, Object> blackboard = new ConcurrentHashMap<>();
    private final List<String> trace = new CopyOnWriteArrayList<>();
    private int round;

    public AgentContext(String task) {
        this.task = task;
    }

    public String task() {
        return task;
    }

    public AgentContext put(String key, Object value) {
        if (value != null) {
            blackboard.put(key, value);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) blackboard.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        Object value = blackboard.get(key);
        return value == null ? defaultValue : (T) value;
    }

    public boolean contains(String key) {
        return blackboard.containsKey(key);
    }

    /** 记录一条执行轨迹，供前端/日志展示 */
    public void log(String message) {
        trace.add(message);
    }

    public List<String> trace() {
        return List.copyOf(trace);
    }

    public int round() {
        return round;
    }

    public void round(int round) {
        this.round = round;
    }
}