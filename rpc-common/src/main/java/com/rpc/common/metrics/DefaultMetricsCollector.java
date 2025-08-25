package com.rpc.common.metrics;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 默认的性能指标收集器实现
 * 
 * 线程安全的指标收集实现，使用内存存储统计数据
 */
@Slf4j
public class DefaultMetricsCollector implements MetricsCollector {
    
    private final String name;
    private final ConcurrentMap<String, ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConnectionPoolMetrics> connectionPoolMetricsMap = new ConcurrentHashMap<>();
    
    public DefaultMetricsCollector() {
        this("default");
    }
    
    public DefaultMetricsCollector(String name) {
        this.name = name;
    }
    
    @Override
    public void recordRequestTime(String serviceName, String methodName, long duration) {
        ServiceMetrics serviceMetrics = getOrCreateServiceMetrics(serviceName);
        MethodMetrics methodMetrics = serviceMetrics.getOrCreateMethodMetrics(methodName);
        
        serviceMetrics.recordResponseTime(duration);
        methodMetrics.recordResponseTime(duration);
    }
    
    @Override
    public void recordError(String serviceName, String methodName, Throwable error) {
        ServiceMetrics serviceMetrics = getOrCreateServiceMetrics(serviceName);
        MethodMetrics methodMetrics = serviceMetrics.getOrCreateMethodMetrics(methodName);
        
        serviceMetrics.recordError();
        methodMetrics.recordError();
        
        log.debug("记录错误: {}#{} - {}", serviceName, methodName, error.getMessage());
    }
    
    @Override
    public void recordSuccess(String serviceName, String methodName) {
        ServiceMetrics serviceMetrics = getOrCreateServiceMetrics(serviceName);
        MethodMetrics methodMetrics = serviceMetrics.getOrCreateMethodMetrics(methodName);
        
        serviceMetrics.recordSuccess();
        methodMetrics.recordSuccess();
    }
    
    @Override
    public void recordThroughput(String serviceName, String methodName) {
        ServiceMetrics serviceMetrics = getOrCreateServiceMetrics(serviceName);
        serviceMetrics.recordRequest();
    }
    
    @Override
    public void recordConnectionPool(String serverAddress, int activeConnections, int totalConnections, int waitingRequests) {
        ConnectionPoolMetrics metrics = connectionPoolMetricsMap.computeIfAbsent(serverAddress, 
            key -> new ConnectionPoolMetrics(serverAddress));
        
        metrics.update(activeConnections, totalConnections, waitingRequests);
    }
    
    @Override
    public MetricsSnapshot getSnapshot(String serviceName) {
        ServiceMetrics serviceMetrics = serviceMetricsMap.get(serviceName);
        if (serviceMetrics == null) {
            return MetricsSnapshot.builder()
                    .timestamp(System.currentTimeMillis())
                    .serviceName(serviceName)
                    .build();
        }
        
        return serviceMetrics.createSnapshot();
    }
    
    @Override
    public MetricsSnapshot getAllSnapshot() {
        MetricsSnapshot.MetricsSnapshotBuilder builder = MetricsSnapshot.builder()
                .timestamp(System.currentTimeMillis())
                .serviceName("ALL");
        
        long totalRequests = 0;
        long successRequests = 0;
        long failedRequests = 0;
        double totalResponseTime = 0;
        long minResponseTime = Long.MAX_VALUE;
        long maxResponseTime = 0;
        
        for (ServiceMetrics serviceMetrics : serviceMetricsMap.values()) {
            totalRequests += serviceMetrics.totalRequests.sum();
            successRequests += serviceMetrics.successRequests.sum();
            failedRequests += serviceMetrics.failedRequests.sum();
            
            if (serviceMetrics.minResponseTime.get() < minResponseTime) {
                minResponseTime = serviceMetrics.minResponseTime.get();
            }
            if (serviceMetrics.maxResponseTime.get() > maxResponseTime) {
                maxResponseTime = serviceMetrics.maxResponseTime.get();
            }
        }
        
        double averageResponseTime = totalRequests > 0 ? totalResponseTime / totalRequests : 0;
        double errorRate = totalRequests > 0 ? (double) failedRequests / totalRequests : 0;
        
        return builder
                .totalRequests(totalRequests)
                .successRequests(successRequests)
                .failedRequests(failedRequests)
                .averageResponseTime(averageResponseTime)
                .minResponseTime(minResponseTime == Long.MAX_VALUE ? 0 : minResponseTime)
                .maxResponseTime(maxResponseTime)
                .errorRate(errorRate)
                .build();
    }
    
    @Override
    public void reset() {
        serviceMetricsMap.clear();
        connectionPoolMetricsMap.clear();
        log.info("指标收集器已重置: {}", name);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    private ServiceMetrics getOrCreateServiceMetrics(String serviceName) {
        return serviceMetricsMap.computeIfAbsent(serviceName, ServiceMetrics::new);
    }
    
    /**
     * 服务级别的指标统计
     */
    private static class ServiceMetrics {
        private final String serviceName;
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder successRequests = new LongAdder();
        private final LongAdder failedRequests = new LongAdder();
        private final LongAdder totalResponseTime = new LongAdder();
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        private final AtomicLong lastRequestTime = new AtomicLong(System.currentTimeMillis());
        private final ConcurrentMap<String, MethodMetrics> methodMetricsMap = new ConcurrentHashMap<>();
        private final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        
        public ServiceMetrics(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public void recordRequest() {
            totalRequests.increment();
            lastRequestTime.set(System.currentTimeMillis());
        }
        
        public void recordSuccess() {
            successRequests.increment();
            recordRequest();
        }
        
        public void recordError() {
            failedRequests.increment();
            recordRequest();
        }
        
        public void recordResponseTime(long duration) {
            totalResponseTime.add(duration);
            
            // 更新最小/最大响应时间
            long currentMin = minResponseTime.get();
            while (duration < currentMin && !minResponseTime.compareAndSet(currentMin, duration)) {
                currentMin = minResponseTime.get();
            }
            
            long currentMax = maxResponseTime.get();
            while (duration > currentMax && !maxResponseTime.compareAndSet(currentMax, duration)) {
                currentMax = maxResponseTime.get();
            }
            
            // 保存响应时间用于百分位计算（限制大小避免内存泄漏）
            synchronized (responseTimes) {
                responseTimes.add(duration);
                if (responseTimes.size() > 10000) {
                    responseTimes.subList(0, 5000).clear(); // 保留最近的5000个
                }
            }
        }
        
        public MethodMetrics getOrCreateMethodMetrics(String methodName) {
            return methodMetricsMap.computeIfAbsent(methodName, MethodMetrics::new);
        }
        
        public MetricsSnapshot createSnapshot() {
            long total = totalRequests.sum();
            long success = successRequests.sum();
            long failed = failedRequests.sum();
            
            double avgResponseTime = total > 0 ? (double) totalResponseTime.sum() / total : 0;
            double errorRate = total > 0 ? (double) failed / total : 0;
            
            // 计算QPS（基于最近的请求时间）
            long timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime.get();
            double qps = timeSinceLastRequest < 60000 ? total / Math.max(1, timeSinceLastRequest / 1000.0) : 0;
            
            // 计算百分位响应时间
            long p95 = calculatePercentile(95);
            long p99 = calculatePercentile(99);
            
            return MetricsSnapshot.builder()
                    .timestamp(System.currentTimeMillis())
                    .serviceName(serviceName)
                    .totalRequests(total)
                    .successRequests(success)
                    .failedRequests(failed)
                    .averageResponseTime(avgResponseTime)
                    .minResponseTime(minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get())
                    .maxResponseTime(maxResponseTime.get())
                    .qps(qps)
                    .errorRate(errorRate)
                    .p95ResponseTime(p95)
                    .p99ResponseTime(p99)
                    .build();
        }
        
        private long calculatePercentile(int percentile) {
            synchronized (responseTimes) {
                if (responseTimes.isEmpty()) return 0;
                
                List<Long> sortedTimes = new ArrayList<>(responseTimes);
                Collections.sort(sortedTimes);
                
                int index = (int) Math.ceil(percentile / 100.0 * sortedTimes.size()) - 1;
                index = Math.max(0, Math.min(index, sortedTimes.size() - 1));
                
                return sortedTimes.get(index);
            }
        }
    }
    
    /**
     * 方法级别的指标统计
     */
    private static class MethodMetrics {
        private final String methodName;
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successCalls = new LongAdder();
        private final LongAdder failedCalls = new LongAdder();
        private final LongAdder totalResponseTime = new LongAdder();
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        
        public MethodMetrics(String methodName) {
            this.methodName = methodName;
        }
        
        public void recordSuccess() {
            successCalls.increment();
            totalCalls.increment();
        }
        
        public void recordError() {
            failedCalls.increment();
            totalCalls.increment();
        }
        
        public void recordResponseTime(long duration) {
            totalResponseTime.add(duration);
            
            long currentMin = minResponseTime.get();
            while (duration < currentMin && !minResponseTime.compareAndSet(currentMin, duration)) {
                currentMin = minResponseTime.get();
            }
            
            long currentMax = maxResponseTime.get();
            while (duration > currentMax && !maxResponseTime.compareAndSet(currentMax, duration)) {
                currentMax = maxResponseTime.get();
            }
        }
    }
    
    /**
     * 连接池指标统计
     */
    private static class ConnectionPoolMetrics {
        private final String serverAddress;
        private volatile int totalConnections;
        private volatile int activeConnections;
        private volatile int waitingRequests;
        private volatile long lastUpdateTime;
        
        public ConnectionPoolMetrics(String serverAddress) {
            this.serverAddress = serverAddress;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void update(int activeConnections, int totalConnections, int waitingRequests) {
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.waitingRequests = waitingRequests;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
}
