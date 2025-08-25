package com.rpc.common.config;

import com.rpc.common.retry.DefaultRetryPolicy;
import com.rpc.common.retry.RetryPolicy;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * RPC配置类
 */
@Data
@Builder
public class RpcConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 请求超时时间（毫秒）
     */
    @Builder.Default
    private long requestTimeout = 5000;
    
    /**
     * 连接超时时间（毫秒）
     */
    @Builder.Default
    private long connectTimeout = 3000;
    
    /**
     * 重试策略
     */
    @Builder.Default
    private RetryPolicy retryPolicy = new DefaultRetryPolicy();
    
    /**
     * 是否启用重试
     */
    @Builder.Default
    private boolean retryEnabled = true;
    
    /**
     * 是否启用熔断器
     */
    @Builder.Default
    private boolean circuitBreakerEnabled = true;
    
    /**
     * 熔断器失败阈值
     */
    @Builder.Default
    private int circuitBreakerFailureThreshold = 5;
    
    /**
     * 熔断器恢复时间（毫秒）
     */
    @Builder.Default
    private long circuitBreakerRecoveryTimeout = 60000;
    
    /**
     * 序列化类型
     */
    @Builder.Default
    private String serializationType = "json";
    
    /**
     * 压缩类型
     */
    @Builder.Default
    private String compressionType = "gzip";
    
    /**
     * 负载均衡策略
     */
    @Builder.Default
    private String loadBalanceStrategy = "random";
    
    /**
     * ZooKeeper连接字符串
     */
    @Builder.Default
    private String zookeeperAddress = "localhost:2181";
    
    /**
     * ZooKeeper会话超时时间
     */
    @Builder.Default
    private int zookeeperSessionTimeout = 30000;
    
    /**
     * ZooKeeper连接超时时间
     */
    @Builder.Default
    private int zookeeperConnectionTimeout = 10000;
    
    /**
     * 连接池配置
     */
    @Builder.Default
    private ConnectionPoolConfig connectionPoolConfig = ConnectionPoolConfig.defaultConfig();
    
    /**
     * 获取默认配置
     */
    public static RpcConfig defaultConfig() {
        return RpcConfig.builder().build();
    }
    
    /**
     * 创建高性能配置
     */
    public static RpcConfig highPerformanceConfig() {
        return RpcConfig.builder()
                .requestTimeout(3000)
                .connectTimeout(2000)
                .serializationType("protobuf")
                .compressionType("none")
                .loadBalanceStrategy("roundrobin")
                .retryEnabled(false)
                .connectionPoolConfig(ConnectionPoolConfig.highPerformanceConfig())
                .build();
    }
    
    /**
     * 创建高可用配置
     */
    public static RpcConfig highAvailabilityConfig() {
        return RpcConfig.builder()
                .requestTimeout(10000)
                .connectTimeout(5000)
                .retryEnabled(true)
                .retryPolicy(DefaultRetryPolicy.exponentialBackoff(3, 1000, 2.0, 10000))
                .circuitBreakerEnabled(true)
                .circuitBreakerFailureThreshold(3)
                .circuitBreakerRecoveryTimeout(30000)
                .build();
    }
}
