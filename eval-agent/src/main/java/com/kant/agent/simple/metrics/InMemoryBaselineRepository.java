package com.kant.agent.simple.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化实现：内存 + 本地 JSON 文件落盘。
 * 生产环境可换 DB 实现，只需实现 BaselineRepository 接口。
 */
@Slf4j
@Repository
public class InMemoryBaselineRepository implements BaselineRepository {

    private static final Path STORAGE = Paths.get("data", "baseline-snapshots.json");

    private final Map<String, BaselineSnapshot> store = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @PostConstruct
    public void load() {
        if (!Files.exists(STORAGE)) return;
        try {
            List<BaselineSnapshot> list = mapper.readValue(STORAGE.toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, BaselineSnapshot.class));
            list.forEach(s -> store.put(s.snapshotId(), s));
            log.info("加载历史 baseline 快照 {} 条", list.size());
        } catch (IOException e) {
            log.warn("加载 baseline 快照失败，忽略: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void save(BaselineSnapshot snapshot) {
        store.put(snapshot.snapshotId(), snapshot);
        flush();
    }

    @Override
    public Optional<BaselineSnapshot> latestByLabel(String label) {
        return store.values().stream()
                .filter(s -> label.equals(s.label()))
                .max(Comparator.comparing(BaselineSnapshot::createdAt));
    }

    @Override
    public List<BaselineSnapshot> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(BaselineSnapshot::createdAt))
                .toList();
    }

    private void flush() {
        try {
            Files.createDirectories(STORAGE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(STORAGE.toFile(), findAll());
        } catch (IOException e) {
            log.error("落盘 baseline 快照失败", e);
        }
    }
}