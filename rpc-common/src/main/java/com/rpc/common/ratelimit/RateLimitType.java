package com.rpc.common.ratelimit;

/**
 * 限流类型枚举
 */
public enum RateLimitType {
    
    /**
     * 令牌桶算法
     * 特点：允许突发流量，适合处理不均匀的请求
     */
    TOKEN_BUCKET("令牌桶"),
    
    /**
     * 滑动窗口算法
     * 特点：更精确的流量控制，避免突发流量
     */
    SLIDING_WINDOW("滑动窗口");
    
    private final String description;
    
    RateLimitType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
