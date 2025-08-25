package com.rpc.common.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 令牌桶限流器
 * 
 * 特点：
 * 1. 允许突发流量
 * 2. 平滑处理请求
 * 3. 线程安全
 */
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {
    
    private final RateLimitConfig config;
    private final AtomicReference<BucketState> bucketState;
    private final AtomicLong totalRequests;
    private final AtomicLong limitedRequests;
    private final AtomicLong lastResetTime;
    
    public TokenBucketRateLimiter(RateLimitConfig config) {
        if (!config.isValid()) {
            throw new IllegalArgumentException("无效的限流配置: " + config);
        }
        
        this.config = config;
        this.bucketState = new AtomicReference<>(new BucketState(config.getCapacity(), System.currentTimeMillis()));
        this.totalRequests = new AtomicLong(0);
        this.limitedRequests = new AtomicLong(0);
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());
        
        log.debug("初始化令牌桶限流器: {}", config.getDescription());
    }
    
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }
    
    @Override
    public boolean tryAcquire(int permits) {
        if (!config.isEnabled()) {
            return true;
        }
        
        if (permits <= 0) {
            return true;
        }
        
        totalRequests.incrementAndGet();
        
        long now = System.currentTimeMillis();
        
        while (true) {
            BucketState currentState = bucketState.get();
            BucketState newState = refillTokens(currentState, now);
            
            if (newState.tokens >= permits) {
                // 有足够的令牌
                BucketState updatedState = new BucketState(
                        newState.tokens - permits, 
                        newState.lastRefillTime
                );
                
                if (bucketState.compareAndSet(currentState, updatedState)) {
                    log.debug("令牌获取成功: permits={}, remaining={}", permits, updatedState.tokens);
                    return true;
                }
                // CAS失败，重试
            } else {
                // 令牌不足
                if (bucketState.compareAndSet(currentState, newState)) {
                    limitedRequests.incrementAndGet();
                    log.debug("令牌不足，请求被限流: permits={}, available={}", permits, newState.tokens);
                    return false;
                }
                // CAS失败，重试
            }
        }
    }
    
    @Override
    public RateLimitResult getStatus() {
        long now = System.currentTimeMillis();
        BucketState currentState = bucketState.get();
        BucketState newState = refillTokens(currentState, now);
        
        // 更新状态（尽力而为，失败也没关系）
        bucketState.compareAndSet(currentState, newState);
        
        long total = totalRequests.get();
        long limited = limitedRequests.get();
        
        // 计算当前QPS（基于最近的请求统计）
        double currentQps = calculateCurrentQps();
        
        return RateLimitResult.allowed(
                newState.tokens,
                total,
                limited,
                currentQps
        );
    }
    
    @Override
    public void reset() {
        bucketState.set(new BucketState(config.getCapacity(), System.currentTimeMillis()));
        totalRequests.set(0);
        limitedRequests.set(0);
        lastResetTime.set(System.currentTimeMillis());
        log.debug("令牌桶限流器已重置");
    }
    
    @Override
    public RateLimitConfig getConfig() {
        return config;
    }
    
    /**
     * 重新填充令牌
     */
    private BucketState refillTokens(BucketState currentState, long now) {
        if (now <= currentState.lastRefillTime) {
            return currentState;
        }
        
        // 计算需要添加的令牌数
        long timePassed = now - currentState.lastRefillTime;
        double tokensToAdd = (timePassed / 1000.0) * config.getRate();
        
        int newTokens = Math.min(
                config.getCapacity(),
                currentState.tokens + (int) tokensToAdd
        );
        
        return new BucketState(newTokens, now);
    }
    
    /**
     * 计算当前QPS
     */
    private double calculateCurrentQps() {
        long now = System.currentTimeMillis();
        long resetTime = lastResetTime.get();
        long elapsedSeconds = Math.max(1, (now - resetTime) / 1000);
        
        return (double) totalRequests.get() / elapsedSeconds;
    }
    
    /**
     * 令牌桶状态
     */
    private static class BucketState {
        final int tokens;
        final long lastRefillTime;
        
        BucketState(int tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
        
        @Override
        public String toString() {
            return String.format("BucketState{tokens=%d, lastRefill=%d}", tokens, lastRefillTime);
        }
    }
}
