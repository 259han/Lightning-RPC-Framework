package com.rpc.client.pool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 连接池统计信息
 * 
 * 提供连接池的运行时统计数据，用于监控和调优
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolStats {
    
    /**
     * 服务器地址
     */
    private String serverAddress;
    
    /**
     * 总连接数
     */
    private int totalConnections;
    
    /**
     * 活跃连接数（包括使用中和可用的）
     */
    private int activeConnections;
    
    /**
     * 可用连接数
     */
    private int availableConnections;
    
    /**
     * 等待中的请求数
     */
    private int waitingRequests;
    
    /**
     * 累计创建连接数
     */
    private long connectionCreateCount;
    
    /**
     * 累计关闭连接数
     */
    private long connectionCloseCount;
    
    /**
     * 累计等待请求数
     */
    private long requestWaitCount;
    
    /**
     * 获取使用中的连接数
     */
    public int getInUseConnections() {
        return totalConnections - availableConnections;
    }
    
    /**
     * 获取连接池使用率
     */
    public double getUtilizationRate() {
        if (totalConnections == 0) return 0.0;
        return (double) getInUseConnections() / totalConnections;
    }
    
    /**
     * 检查连接池是否健康
     */
    public boolean isHealthy() {
        // 简单的健康检查：有可用连接且等待请求不太多
        return availableConnections > 0 || (totalConnections > 0 && waitingRequests < 10);
    }
    
    /**
     * 获取连接池状态描述
     */
    public String getStatusDescription() {
        if (!isHealthy()) {
            return "UNHEALTHY";
        } else if (getUtilizationRate() > 0.9) {
            return "HIGH_LOAD";
        } else if (getUtilizationRate() > 0.7) {
            return "MEDIUM_LOAD";
        } else {
            return "NORMAL";
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "ConnectionPoolStats{server='%s', total=%d, active=%d, available=%d, " +
            "inUse=%d, waiting=%d, utilization=%.2f%%, status=%s}",
            serverAddress, totalConnections, activeConnections, availableConnections,
            getInUseConnections(), waitingRequests, getUtilizationRate() * 100,
            getStatusDescription()
        );
    }
}
