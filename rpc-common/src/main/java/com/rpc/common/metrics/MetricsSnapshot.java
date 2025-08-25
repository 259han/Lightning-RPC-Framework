package com.rpc.common.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能指标快照
 * 
 * 包含某个时间点的性能统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSnapshot {
    
    /**
     * 快照时间戳
     */
    private long timestamp;
    
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 总请求数
     */
    @Builder.Default
    private long totalRequests = 0;
    
    /**
     * 成功请求数
     */
    @Builder.Default
    private long successRequests = 0;
    
    /**
     * 失败请求数
     */
    @Builder.Default
    private long failedRequests = 0;
    
    /**
     * 平均响应时间（毫秒）
     */
    @Builder.Default
    private double averageResponseTime = 0.0;
    
    /**
     * 最小响应时间（毫秒）
     */
    @Builder.Default
    private long minResponseTime = Long.MAX_VALUE;
    
    /**
     * 最大响应时间（毫秒）
     */
    @Builder.Default
    private long maxResponseTime = 0;
    
    /**
     * QPS（每秒请求数）
     */
    @Builder.Default
    private double qps = 0.0;
    
    /**
     * 错误率（0-1之间）
     */
    @Builder.Default
    private double errorRate = 0.0;
    
    /**
     * P95响应时间（毫秒）
     */
    @Builder.Default
    private long p95ResponseTime = 0;
    
    /**
     * P99响应时间（毫秒）
     */
    @Builder.Default
    private long p99ResponseTime = 0;
    
    /**
     * 各方法的详细统计
     */
    @Builder.Default
    private Map<String, MethodMetrics> methodMetrics = new ConcurrentHashMap<>();
    
    /**
     * 连接池统计
     */
    @Builder.Default
    private Map<String, ConnectionPoolMetrics> connectionPoolMetrics = new ConcurrentHashMap<>();
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (totalRequests == 0) return 1.0;
        return (double) successRequests / totalRequests;
    }
    
    /**
     * 检查服务是否健康
     */
    public boolean isHealthy() {
        // 简单的健康检查：错误率低于5%且有成功请求
        return errorRate < 0.05 && successRequests > 0;
    }
    
    /**
     * 获取服务状态描述
     */
    public String getStatusDescription() {
        if (!isHealthy()) {
            return "UNHEALTHY";
        } else if (errorRate > 0.01) {
            return "WARNING";
        } else if (averageResponseTime > 1000) {
            return "SLOW";
        } else {
            return "HEALTHY";
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "MetricsSnapshot{service='%s', total=%d, success=%d, failed=%d, " +
            "avgTime=%.2fms, qps=%.2f, errorRate=%.2f%%, status=%s}",
            serviceName, totalRequests, successRequests, failedRequests,
            averageResponseTime, qps, errorRate * 100, getStatusDescription()
        );
    }
    
    /**
     * 方法级别的指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodMetrics {
        private String methodName;
        private long totalCalls;
        private long successCalls;
        private long failedCalls;
        private double averageResponseTime;
        private long minResponseTime;
        private long maxResponseTime;
        
        public double getSuccessRate() {
            if (totalCalls == 0) return 1.0;
            return (double) successCalls / totalCalls;
        }
        
        public double getErrorRate() {
            if (totalCalls == 0) return 0.0;
            return (double) failedCalls / totalCalls;
        }
    }
    
    /**
     * 连接池指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionPoolMetrics {
        private String serverAddress;
        private int totalConnections;
        private int activeConnections;
        private int waitingRequests;
        private double utilizationRate;
        
        public boolean isHealthy() {
            return utilizationRate < 0.9 && waitingRequests < totalConnections;
        }
    }
}
