package com.rpc.common.metrics;

/**
 * 性能指标收集器接口
 * 
 * 用于收集RPC调用的各种性能指标，支持实时监控和统计分析
 */
public interface MetricsCollector {
    
    /**
     * 记录请求响应时间
     * 
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param duration 响应时间（毫秒）
     */
    void recordRequestTime(String serviceName, String methodName, long duration);
    
    /**
     * 记录错误信息
     * 
     * @param serviceName 服务名称  
     * @param methodName 方法名称
     * @param error 错误信息
     */
    void recordError(String serviceName, String methodName, Throwable error);
    
    /**
     * 记录请求成功
     * 
     * @param serviceName 服务名称
     * @param methodName 方法名称
     */
    void recordSuccess(String serviceName, String methodName);
    
    /**
     * 记录吞吐量（QPS）
     * 
     * @param serviceName 服务名称
     * @param methodName 方法名称
     */
    void recordThroughput(String serviceName, String methodName);
    
    /**
     * 记录连接池使用情况
     * 
     * @param serverAddress 服务器地址
     * @param activeConnections 活跃连接数
     * @param totalConnections 总连接数
     * @param waitingRequests 等待请求数
     */
    void recordConnectionPool(String serverAddress, int activeConnections, int totalConnections, int waitingRequests);
    
    /**
     * 获取指定服务的性能快照
     * 
     * @param serviceName 服务名称
     * @return 性能快照
     */
    MetricsSnapshot getSnapshot(String serviceName);
    
    /**
     * 获取所有服务的性能快照
     * 
     * @return 所有服务的性能快照
     */
    MetricsSnapshot getAllSnapshot();
    
    /**
     * 重置所有指标
     */
    void reset();
    
    /**
     * 获取收集器名称
     */
    String getName();
}
