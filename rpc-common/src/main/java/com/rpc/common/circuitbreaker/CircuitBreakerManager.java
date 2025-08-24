package com.rpc.common.circuitbreaker;

import com.rpc.common.config.RpcConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 熔断器管理器
 */
@Slf4j
public class CircuitBreakerManager {
    
    private static final CircuitBreakerManager INSTANCE = new CircuitBreakerManager();
    
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private RpcConfig config = RpcConfig.defaultConfig();
    
    private CircuitBreakerManager() {}
    
    public static CircuitBreakerManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 设置配置
     */
    public void setConfig(RpcConfig config) {
        this.config = config;
    }
    
    /**
     * 获取或创建熔断器
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, name -> {
            CircuitBreaker circuitBreaker = new CircuitBreaker(
                name, 
                config.getCircuitBreakerFailureThreshold(),
                config.getCircuitBreakerRecoveryTimeout()
            );
            log.info("创建熔断器: {} (失败阈值: {}, 恢复时间: {}ms)", 
                    name, 
                    config.getCircuitBreakerFailureThreshold(),
                    config.getCircuitBreakerRecoveryTimeout());
            return circuitBreaker;
        });
    }
    
    /**
     * 移除熔断器
     */
    public void removeCircuitBreaker(String serviceName) {
        CircuitBreaker removed = circuitBreakers.remove(serviceName);
        if (removed != null) {
            log.info("移除熔断器: {}", serviceName);
        }
    }
    
    /**
     * 获取所有熔断器状态
     */
    public ConcurrentMap<String, CircuitBreaker> getAllCircuitBreakers() {
        return new ConcurrentHashMap<>(circuitBreakers);
    }
    
    /**
     * 重置所有熔断器
     */
    public void resetAllCircuitBreakers() {
        circuitBreakers.values().forEach(CircuitBreaker::forceReset);
        log.info("重置所有熔断器");
    }
    
    /**
     * 清理所有熔断器
     */
    public void clear() {
        circuitBreakers.clear();
        log.info("清理所有熔断器");
    }
}
