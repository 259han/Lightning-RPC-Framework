package com.rpc.common.ratelimit;

/**
 * 限流器接口
 */
public interface RateLimiter {
    
    /**
     * 尝试获取令牌
     * 
     * @return true 如果获取成功，false 如果被限流
     */
    boolean tryAcquire();
    
    /**
     * 尝试获取指定数量的令牌
     * 
     * @param permits 令牌数量
     * @return true 如果获取成功，false 如果被限流
     */
    boolean tryAcquire(int permits);
    
    /**
     * 获取限流器状态
     */
    RateLimitResult getStatus();
    
    /**
     * 重置限流器状态
     */
    void reset();
    
    /**
     * 获取限流器配置
     */
    RateLimitConfig getConfig();
}
