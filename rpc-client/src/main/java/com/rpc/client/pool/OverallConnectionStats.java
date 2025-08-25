package com.rpc.client.pool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 总体连接统计信息
 * 
 * 汇总所有连接池的统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverallConnectionStats {
    
    /**
     * 总连接池数量
     */
    private int totalPools;
    
    /**
     * 总连接数
     */
    private int totalConnections;
    
    /**
     * 总活跃连接数
     */
    private int totalActiveConnections;
    
    /**
     * 总可用连接数
     */
    private int totalAvailableConnections;
    
    /**
     * 总等待请求数
     */
    private int totalWaitingRequests;
    
    /**
     * 累计创建连接数
     */
    private long totalConnectionsCreated;
    
    /**
     * 累计关闭连接数
     */
    private long totalConnectionsClosed;
    
    /**
     * 获取总使用中连接数
     */
    public int getTotalInUseConnections() {
        return totalConnections - totalAvailableConnections;
    }
    
    /**
     * 获取总体使用率
     */
    public double getOverallUtilizationRate() {
        if (totalConnections == 0) return 0.0;
        return (double) getTotalInUseConnections() / totalConnections;
    }
    
    /**
     * 获取平均每个池的连接数
     */
    public double getAverageConnectionsPerPool() {
        if (totalPools == 0) return 0.0;
        return (double) totalConnections / totalPools;
    }
    
    /**
     * 检查总体健康状态
     */
    public boolean isOverallHealthy() {
        // 简单的健康检查规则
        return totalConnections > 0 && 
               totalWaitingRequests < totalConnections * 2 &&
               getOverallUtilizationRate() < 0.95;
    }
    
    /**
     * 获取总体状态描述
     */
    public String getOverallStatus() {
        if (!isOverallHealthy()) {
            return "CRITICAL";
        } else if (getOverallUtilizationRate() > 0.8) {
            return "HIGH_LOAD";
        } else if (getOverallUtilizationRate() > 0.6) {
            return "MEDIUM_LOAD";
        } else {
            return "NORMAL";
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "OverallStats{pools=%d, connections=%d, active=%d, available=%d, " +
            "inUse=%d, waiting=%d, utilization=%.2f%%, avgPerPool=%.1f, status=%s}",
            totalPools, totalConnections, totalActiveConnections, totalAvailableConnections,
            getTotalInUseConnections(), totalWaitingRequests, getOverallUtilizationRate() * 100,
            getAverageConnectionsPerPool(), getOverallStatus()
        );
    }
}
