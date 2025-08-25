package com.rpc.common.ratelimit;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流管理器
 * 
 * 功能：
 * 1. 管理多个限流器实例
 * 2. 按服务/方法级别限流
 * 3. 限流统计和监控
 */
@Slf4j
public class RateLimitManager {
    
    private static volatile RateLimitManager instance;
    
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters;
    private final RateLimitConfig defaultConfig;
    
    private RateLimitManager() {
        this.rateLimiters = new ConcurrentHashMap<>();
        this.defaultConfig = RateLimitConfig.defaultConfig();
        log.info("限流管理器已初始化，默认配置: {}", defaultConfig.getDescription());
    }
    
    public static RateLimitManager getInstance() {
        if (instance == null) {
            synchronized (RateLimitManager.class) {
                if (instance == null) {
                    instance = new RateLimitManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取或创建限流器
     */
    public RateLimiter getRateLimiter(String key) {
        return getRateLimiter(key, defaultConfig);
    }
    
    /**
     * 获取或创建限流器（指定配置）
     */
    public RateLimiter getRateLimiter(String key, RateLimitConfig config) {
        return rateLimiters.computeIfAbsent(key, k -> createRateLimiter(config));
    }
    
    /**
     * 服务级别限流检查
     */
    public boolean checkServiceRateLimit(String serviceName) {
        String key = "service:" + serviceName;
        RateLimiter rateLimiter = getRateLimiter(key);
        boolean allowed = rateLimiter.tryAcquire();
        
        if (!allowed) {
            log.warn("服务限流触发: service={}", serviceName);
        }
        
        return allowed;
    }
    
    /**
     * 方法级别限流检查
     */
    public boolean checkMethodRateLimit(String serviceName, String methodName) {
        String key = "method:" + serviceName + "#" + methodName;
        RateLimiter rateLimiter = getRateLimiter(key);
        boolean allowed = rateLimiter.tryAcquire();
        
        if (!allowed) {
            log.warn("方法限流触发: service={}, method={}", serviceName, methodName);
        }
        
        return allowed;
    }
    
    /**
     * 用户级别限流检查
     */
    public boolean checkUserRateLimit(String userId) {
        String key = "user:" + userId;
        RateLimiter rateLimiter = getRateLimiter(key);
        boolean allowed = rateLimiter.tryAcquire();
        
        if (!allowed) {
            log.warn("用户限流触发: user={}", userId);
        }
        
        return allowed;
    }
    
    /**
     * IP级别限流检查
     */
    public boolean checkIpRateLimit(String ipAddress) {
        String key = "ip:" + ipAddress;
        RateLimiter rateLimiter = getRateLimiter(key);
        boolean allowed = rateLimiter.tryAcquire();
        
        if (!allowed) {
            log.warn("IP限流触发: ip={}", ipAddress);
        }
        
        return allowed;
    }
    
    /**
     * 获取限流状态
     */
    public RateLimitResult getRateLimitStatus(String key) {
        RateLimiter rateLimiter = rateLimiters.get(key);
        if (rateLimiter != null) {
            return rateLimiter.getStatus();
        }
        return null;
    }
    
    /**
     * 获取所有限流器状态
     */
    public java.util.Map<String, RateLimitResult> getAllRateLimitStatus() {
        java.util.Map<String, RateLimitResult> statusMap = new java.util.HashMap<>();
        
        rateLimiters.forEach((key, rateLimiter) -> {
            statusMap.put(key, rateLimiter.getStatus());
        });
        
        return statusMap;
    }
    
    /**
     * 重置指定限流器
     */
    public void resetRateLimiter(String key) {
        RateLimiter rateLimiter = rateLimiters.get(key);
        if (rateLimiter != null) {
            rateLimiter.reset();
            log.info("重置限流器: {}", key);
        }
    }
    
    /**
     * 重置所有限流器
     */
    public void resetAllRateLimiters() {
        rateLimiters.forEach((key, rateLimiter) -> {
            rateLimiter.reset();
        });
        log.info("重置所有限流器，数量: {}", rateLimiters.size());
    }
    
    /**
     * 移除限流器
     */
    public void removeRateLimiter(String key) {
        RateLimiter removed = rateLimiters.remove(key);
        if (removed != null) {
            log.info("移除限流器: {}", key);
        }
    }
    
    /**
     * 获取限流统计信息
     */
    public String getRateLimitStats() {
        int totalLimiters = rateLimiters.size();
        
        long totalRequests = 0;
        long totalLimited = 0;
        int activeLimiters = 0;
        
        for (RateLimiter rateLimiter : rateLimiters.values()) {
            RateLimitResult status = rateLimiter.getStatus();
            totalRequests += status.getTotalRequests();
            totalLimited += status.getLimitedRequests();
            
            if (status.getTotalRequests() > 0) {
                activeLimiters++;
            }
        }
        
        double overallLimitRate = totalRequests > 0 ? 
                (double) totalLimited / totalRequests * 100.0 : 0.0;
        
        return String.format("限流统计 - 限流器总数: %d, 活跃: %d, 总请求: %d, " +
                "被限流: %d, 限流率: %.2f%%", 
                totalLimiters, activeLimiters, totalRequests, totalLimited, overallLimitRate);
    }
    
    /**
     * 生成限流报告
     */
    public void generateRateLimitReport() {
        log.info("=== 限流状态报告 ===");
        log.info(getRateLimitStats());
        
        rateLimiters.forEach((key, rateLimiter) -> {
            RateLimitResult status = rateLimiter.getStatus();
            if (status.getTotalRequests() > 0) {
                log.info("限流器[{}]: {}", key, status);
            }
        });
        
        log.info("=== 限流报告结束 ===");
    }
    
    /**
     * 创建限流器
     */
    private RateLimiter createRateLimiter(RateLimitConfig config) {
        switch (config.getType()) {
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(config);
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(config);
            default:
                throw new IllegalArgumentException("不支持的限流类型: " + config.getType());
        }
    }
}
