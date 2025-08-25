package com.rpc.common.metrics;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 指标管理器
 * 
 * 管理多个指标收集器，提供统一的指标收集和报告接口
 */
@Slf4j
public class MetricsManager {
    
    private static final MetricsManager INSTANCE = new MetricsManager();
    
    private final List<MetricsCollector> collectors = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService reportingScheduler;
    private volatile boolean reportingEnabled = false;
    private volatile long reportingInterval = 30; // 30秒
    
    private MetricsManager() {
        this.reportingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsManager-Reporter");
            t.setDaemon(true);
            return t;
        });
        
        // 默认添加一个收集器
        addCollector(new DefaultMetricsCollector("default"));
        
        log.info("指标管理器已初始化");
    }
    
    public static MetricsManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 添加指标收集器
     */
    public void addCollector(MetricsCollector collector) {
        collectors.add(collector);
        log.info("添加指标收集器: {}", collector.getName());
    }
    
    /**
     * 移除指标收集器
     */
    public void removeCollector(MetricsCollector collector) {
        collectors.remove(collector);
        log.info("移除指标收集器: {}", collector.getName());
    }
    
    /**
     * 记录请求响应时间
     */
    public void recordRequestTime(String serviceName, String methodName, long duration) {
        collectors.forEach(collector -> {
            try {
                collector.recordRequestTime(serviceName, methodName, duration);
            } catch (Exception e) {
                log.warn("记录请求时间失败: {}", collector.getName(), e);
            }
        });
    }
    
    /**
     * 记录错误
     */
    public void recordError(String serviceName, String methodName, Throwable error) {
        collectors.forEach(collector -> {
            try {
                collector.recordError(serviceName, methodName, error);
            } catch (Exception e) {
                log.warn("记录错误失败: {}", collector.getName(), e);
            }
        });
    }
    
    /**
     * 记录成功
     */
    public void recordSuccess(String serviceName, String methodName) {
        collectors.forEach(collector -> {
            try {
                collector.recordSuccess(serviceName, methodName);
            } catch (Exception e) {
                log.warn("记录成功失败: {}", collector.getName(), e);
            }
        });
    }
    
    /**
     * 记录吞吐量
     */
    public void recordThroughput(String serviceName, String methodName) {
        collectors.forEach(collector -> {
            try {
                collector.recordThroughput(serviceName, methodName);
            } catch (Exception e) {
                log.warn("记录吞吐量失败: {}", collector.getName(), e);
            }
        });
    }
    
    /**
     * 记录连接池使用情况
     */
    public void recordConnectionPool(String serverAddress, int activeConnections, int totalConnections, int waitingRequests) {
        collectors.forEach(collector -> {
            try {
                collector.recordConnectionPool(serverAddress, activeConnections, totalConnections, waitingRequests);
            } catch (Exception e) {
                log.warn("记录连接池使用情况失败: {}", collector.getName(), e);
            }
        });
    }
    
    /**
     * 获取指定服务的性能快照
     */
    public MetricsSnapshot getSnapshot(String serviceName) {
        // 返回第一个收集器的快照
        if (!collectors.isEmpty()) {
            return collectors.get(0).getSnapshot(serviceName);
        }
        return MetricsSnapshot.builder()
                .timestamp(System.currentTimeMillis())
                .serviceName(serviceName)
                .build();
    }
    
    /**
     * 获取所有服务的性能快照
     */
    public MetricsSnapshot getAllSnapshot() {
        // 返回第一个收集器的快照
        if (!collectors.isEmpty()) {
            return collectors.get(0).getAllSnapshot();
        }
        return MetricsSnapshot.builder()
                .timestamp(System.currentTimeMillis())
                .serviceName("ALL")
                .build();
    }
    
    /**
     * 启用定期报告
     */
    public void enableReporting(long intervalSeconds) {
        if (reportingEnabled) {
            return;
        }
        
        this.reportingInterval = intervalSeconds;
        this.reportingEnabled = true;
        
        reportingScheduler.scheduleWithFixedDelay(this::generateReport, 
                intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        log.info("已启用指标定期报告，间隔: {}秒", intervalSeconds);
    }
    
    /**
     * 禁用定期报告
     */
    public void disableReporting() {
        this.reportingEnabled = false;
        log.info("已禁用指标定期报告");
    }
    
    /**
     * 生成指标报告
     */
    private void generateReport() {
        try {
            if (!reportingEnabled || collectors.isEmpty()) {
                return;
            }
            
            MetricsSnapshot snapshot = getAllSnapshot();
            
            log.info("=== RPC性能指标报告 ===");
            log.info("总体统计: {}", snapshot);
            
            // 如果有详细的方法统计，也输出
            if (!snapshot.getMethodMetrics().isEmpty()) {
                log.info("方法详情:");
                snapshot.getMethodMetrics().forEach((method, metrics) -> {
                    log.info("  {}: 调用{}次, 成功率{:.2f}%, 平均响应时间{:.2f}ms",
                            method, metrics.getTotalCalls(), 
                            metrics.getSuccessRate() * 100,
                            metrics.getAverageResponseTime());
                });
            }
            
            // 连接池统计
            if (!snapshot.getConnectionPoolMetrics().isEmpty()) {
                log.info("连接池统计:");
                snapshot.getConnectionPoolMetrics().forEach((server, metrics) -> {
                    log.info("  {}: {}/{} 连接, 等待{}, 使用率{:.2f}%",
                            server, metrics.getActiveConnections(), 
                            metrics.getTotalConnections(),
                            metrics.getWaitingRequests(),
                            metrics.getUtilizationRate() * 100);
                });
            }
            
            log.info("========================");
            
        } catch (Exception e) {
            log.warn("生成指标报告异常", e);
        }
    }
    
    /**
     * 手动生成一次报告
     */
    public void generateManualReport() {
        generateReport();
    }
    
    /**
     * 重置所有指标
     */
    public void resetAll() {
        collectors.forEach(collector -> {
            try {
                collector.reset();
            } catch (Exception e) {
                log.warn("重置指标收集器失败: {}", collector.getName(), e);
            }
        });
        log.info("所有指标已重置");
    }
    
    /**
     * 获取收集器数量
     */
    public int getCollectorCount() {
        return collectors.size();
    }
    
    /**
     * 检查是否启用了报告
     */
    public boolean isReportingEnabled() {
        return reportingEnabled;
    }
    
    /**
     * 关闭指标管理器
     */
    public void shutdown() {
        disableReporting();
        reportingScheduler.shutdown();
        collectors.clear();
        log.info("指标管理器已关闭");
    }
}
