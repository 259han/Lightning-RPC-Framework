package com.rpc.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 限流结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 是否被限流
     */
    private boolean limited;
    
    /**
     * 当前可用令牌数
     */
    private int availableTokens;
    
    /**
     * 总请求数
     */
    private long totalRequests;
    
    /**
     * 被限流的请求数
     */
    private long limitedRequests;
    
    /**
     * 当前QPS
     */
    private double currentQps;
    
    /**
     * 限流率（被限流请求数/总请求数）
     */
    private double limitRate;
    
    /**
     * 重置时间戳
     */
    private long resetTime;
    
    /**
     * 下次可用时间（毫秒）
     */
    private long nextAvailableTime;
    
    /**
     * 创建允许通过的结果
     */
    public static RateLimitResult allowed() {
        return RateLimitResult.builder()
                .limited(false)
                .build();
    }
    
    /**
     * 创建允许通过的结果（包含状态信息）
     */
    public static RateLimitResult allowed(int availableTokens, long totalRequests, 
                                        long limitedRequests, double currentQps) {
        double limitRate = totalRequests > 0 ? (double) limitedRequests / totalRequests : 0.0;
        
        return RateLimitResult.builder()
                .limited(false)
                .availableTokens(availableTokens)
                .totalRequests(totalRequests)
                .limitedRequests(limitedRequests)
                .currentQps(currentQps)
                .limitRate(limitRate)
                .build();
    }
    
    /**
     * 创建被限流的结果
     */
    public static RateLimitResult limited() {
        return RateLimitResult.builder()
                .limited(true)
                .build();
    }
    
    /**
     * 创建被限流的结果（包含状态信息）
     */
    public static RateLimitResult limited(int availableTokens, long totalRequests, 
                                        long limitedRequests, double currentQps, 
                                        long nextAvailableTime) {
        double limitRate = totalRequests > 0 ? (double) limitedRequests / totalRequests : 0.0;
        
        return RateLimitResult.builder()
                .limited(true)
                .availableTokens(availableTokens)
                .totalRequests(totalRequests)
                .limitedRequests(limitedRequests)
                .currentQps(currentQps)
                .limitRate(limitRate)
                .nextAvailableTime(nextAvailableTime)
                .build();
    }
    
    /**
     * 获取限流率百分比
     */
    public double getLimitRatePercent() {
        return limitRate * 100.0;
    }
    
    /**
     * 检查是否需要告警
     */
    public boolean needsAlert() {
        return limitRate > 0.1; // 限流率超过10%时告警
    }
    
    @Override
    public String toString() {
        if (limited) {
            return String.format("RateLimitResult{limited=true, availableTokens=%d, " +
                    "limitRate=%.2f%%, nextAvailable=%dms}", 
                    availableTokens, getLimitRatePercent(), 
                    Math.max(0, nextAvailableTime - System.currentTimeMillis()));
        } else {
            return String.format("RateLimitResult{limited=false, availableTokens=%d, " +
                    "qps=%.2f, limitRate=%.2f%%}", 
                    availableTokens, currentQps, getLimitRatePercent());
        }
    }
}
