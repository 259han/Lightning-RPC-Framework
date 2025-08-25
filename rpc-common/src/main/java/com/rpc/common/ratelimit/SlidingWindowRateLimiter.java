package com.rpc.common.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口限流器
 * 
 * 特点：
 * 1. 精确的流量控制
 * 2. 避免突发流量
 * 3. 线程安全
 */
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {
    
    private final RateLimitConfig config;
    private final AtomicLongArray windowCounts;
    private final AtomicLong totalRequests;
    private final AtomicLong limitedRequests;
    private final AtomicLong lastResetTime;
    private final long sliceTimeMs;
    
    public SlidingWindowRateLimiter(RateLimitConfig config) {
        if (!config.isValid()) {
            throw new IllegalArgumentException("无效的限流配置: " + config);
        }
        
        this.config = config;
        this.windowCounts = new AtomicLongArray(config.getWindowSlices());
        this.totalRequests = new AtomicLong(0);
        this.limitedRequests = new AtomicLong(0);
        this.lastResetTime = new AtomicLong(System.currentTimeMillis());
        this.sliceTimeMs = config.getWindowSizeMs() / config.getWindowSlices();
        
        log.debug("初始化滑动窗口限流器: {}, 切片时间: {}ms", 
                config.getDescription(), sliceTimeMs);
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
        int currentSlice = getCurrentSlice(now);
        
        // 清理过期的窗口数据
        cleanExpiredSlices(now);
        
        // 计算当前窗口内的总请求数
        long currentWindowCount = getCurrentWindowCount(now);
        
        // 检查是否超过限制
        if (currentWindowCount + permits > config.getRate()) {
            limitedRequests.incrementAndGet();
            log.debug("滑动窗口限流: permits={}, current={}, limit={}", 
                    permits, currentWindowCount, config.getRate());
            return false;
        }
        
        // 增加当前切片的计数
        windowCounts.addAndGet(currentSlice, permits);
        log.debug("滑动窗口通过: permits={}, slice={}, current={}", 
                permits, currentSlice, currentWindowCount + permits);
        
        return true;
    }
    
    @Override
    public RateLimitResult getStatus() {
        long now = System.currentTimeMillis();
        cleanExpiredSlices(now);
        
        long currentWindowCount = getCurrentWindowCount(now);
        int availableTokens = Math.max(0, (int) (config.getRate() - currentWindowCount));
        
        long total = totalRequests.get();
        long limited = limitedRequests.get();
        
        // 计算当前QPS
        double currentQps = calculateCurrentQps(now);
        
        return RateLimitResult.allowed(
                availableTokens,
                total,
                limited,
                currentQps
        );
    }
    
    @Override
    public void reset() {
        for (int i = 0; i < windowCounts.length(); i++) {
            windowCounts.set(i, 0);
        }
        totalRequests.set(0);
        limitedRequests.set(0);
        lastResetTime.set(System.currentTimeMillis());
        log.debug("滑动窗口限流器已重置");
    }
    
    @Override
    public RateLimitConfig getConfig() {
        return config;
    }
    
    /**
     * 获取当前时间对应的切片索引
     */
    private int getCurrentSlice(long timestamp) {
        return (int) ((timestamp / sliceTimeMs) % config.getWindowSlices());
    }
    
    /**
     * 获取指定时间所在窗口的总请求数
     */
    private long getCurrentWindowCount(long now) {
        long count = 0;
        long windowStart = now - config.getWindowSizeMs();
        
        for (int i = 0; i < config.getWindowSlices(); i++) {
            long sliceStart = windowStart + (i * sliceTimeMs);
            long sliceEnd = sliceStart + sliceTimeMs;
            
            // 检查切片是否在当前窗口内
            if (sliceEnd > windowStart && sliceStart <= now) {
                count += windowCounts.get(i);
            }
        }
        
        return count;
    }
    
    /**
     * 清理过期的切片数据
     */
    private void cleanExpiredSlices(long now) {
        long windowStart = now - config.getWindowSizeMs();
        
        for (int i = 0; i < config.getWindowSlices(); i++) {
            long sliceStart = windowStart + (i * sliceTimeMs);
            long sliceEnd = sliceStart + sliceTimeMs;
            
            // 如果切片完全在窗口外，清零
            if (sliceEnd <= windowStart) {
                windowCounts.set(i, 0);
            }
        }
    }
    
    /**
     * 计算当前QPS
     */
    private double calculateCurrentQps(long now) {
        long windowCount = getCurrentWindowCount(now);
        double windowSizeSeconds = config.getWindowSizeMs() / 1000.0;
        return windowCount / windowSizeSeconds;
    }
}
