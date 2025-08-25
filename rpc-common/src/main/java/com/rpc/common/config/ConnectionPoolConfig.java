package com.rpc.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 连接池配置
 * 
 * 用于控制RPC客户端的连接池行为，包括连接数限制、超时设置、健康检查等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoolConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 每个服务器的最大连接数
     */
    @Builder.Default
    private int maxConnectionsPerServer = 10;
    
    /**
     * 连接空闲超时时间（毫秒）
     * 超过此时间的空闲连接将被关闭
     */
    @Builder.Default
    private long connectionIdleTimeout = 300000; // 5分钟
    
    /**
     * 健康检查间隔（毫秒）
     */
    @Builder.Default
    private long healthCheckInterval = 30000; // 30秒
    
    /**
     * 最大等待请求数
     * 当连接池满时，新请求的最大等待队列大小
     */
    @Builder.Default
    private int maxPendingRequests = 1000;
    
    /**
     * 连接建立超时时间（毫秒）
     */
    @Builder.Default
    private long connectTimeout = 5000;
    
    /**
     * 请求超时清理间隔（毫秒）
     */
    @Builder.Default
    private long requestTimeoutCheckInterval = 10000; // 10秒
    
    /**
     * 连接池预热连接数
     * 服务启动时预先建立的连接数
     */
    @Builder.Default
    private int warmupConnections = 2;
    
    /**
     * 是否启用连接池
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 是否启用健康检查
     */
    @Builder.Default
    private boolean healthCheckEnabled = true;
    
    /**
     * 获取默认配置
     */
    public static ConnectionPoolConfig defaultConfig() {
        return ConnectionPoolConfig.builder().build();
    }
    
    /**
     * 获取高性能配置
     */
    public static ConnectionPoolConfig highPerformanceConfig() {
        return ConnectionPoolConfig.builder()
                .maxConnectionsPerServer(20)
                .connectionIdleTimeout(600000) // 10分钟
                .healthCheckInterval(60000)    // 1分钟
                .maxPendingRequests(2000)
                .warmupConnections(5)
                .build();
    }
    
    /**
     * 获取低资源配置
     */
    public static ConnectionPoolConfig lowResourceConfig() {
        return ConnectionPoolConfig.builder()
                .maxConnectionsPerServer(3)
                .connectionIdleTimeout(120000) // 2分钟
                .healthCheckInterval(15000)    // 15秒
                .maxPendingRequests(100)
                .warmupConnections(1)
                .build();
    }
}
