package com.chinatelecom.aigc.evaluate.common.util.threads;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ThreadPoolUtils {
    private final Map<Long, Integer> globalRateLimits;
    private final Map<Long, Semaphore> globalSemaphores;

    // 线程池缓存
    private final Map<String, ThreadPoolExecutor> threadPoolCache;
    private final ReentrantLock lock;
    public ThreadPoolUtils() {
        this.globalRateLimits = new ConcurrentHashMap<>();
        this.globalSemaphores = new ConcurrentHashMap<>();
        this.threadPoolCache = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
    }

    /**
     * 获取或创建线程池（保证同名线程池复用）
     *
     * @param poolName      线程池名称
     * @param corePoolSize  核心线程数
     * @param maxThreadSize 最大线程数
     * @param threadIdleTime 线程空闲时间
     * @param queueSize     队列大小
     * @return 线程池
     */
    public ThreadPoolExecutor getOrCreatePool(String poolName, Integer corePoolSize, Integer maxThreadSize,
                                              Integer threadIdleTime, Integer queueSize) {
        lock.lock();
        try {
            ThreadPoolExecutor executor = threadPoolCache.get(poolName);

            // 检查是否需要更新线程池
            if (executor != null) {
                if (executor.getMaximumPoolSize() != maxThreadSize && executor.getActiveCount() == 0 && executor.getQueue().isEmpty()) {
                    executor.shutdown();
                    try {
                        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                            executor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    threadPoolCache.remove(poolName);
                    executor = null;
                }
            }

            // 重新创建线程池
            if (executor == null) {
                ThreadFactory threadFactory = Executors.defaultThreadFactory();
                executor = new ThreadPoolExecutor(
                    corePoolSize,
                    maxThreadSize,
                    threadIdleTime,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    threadFactory,
                    new ThreadPoolExecutor.CallerRunsPolicy()
                );
                executor.allowCoreThreadTimeOut(true);
                threadPoolCache.put(poolName, executor);
            }

            return executor;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 设置某个域名的全局限速
     *
     * @param taskId 任务名字
     * @param limit  并发限制
     */
    public void setGlobalLimit(Long taskId, int limit) {
        globalRateLimits.put(taskId, limit);
        globalSemaphores.put(taskId, new Semaphore(limit));
    }

    /**
     * 获取某个域名的信号量
     *
     * @param taskId 任务名字
     * @return 该域名的信号量
     */
    public Semaphore getDomainSemaphore(Long taskId) {
        return globalSemaphores.get(taskId);
    }

    /**
     * 关闭指定线程池
     *
     * @param poolName 线程池名称
     */
    public void shutdownPool(String poolName) {
        ThreadPoolExecutor executor = threadPoolCache.remove(poolName);
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * 关闭所有线程池
     */
    public void shutdownAllPools() {
        for (ThreadPoolExecutor executor : threadPoolCache.values()) {
            executor.shutdown();
        }
        threadPoolCache.clear();
    }
}
