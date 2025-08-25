package com.rpc.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 限流配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 限流类型
     */
    @Builder.Default
    private RateLimitType type = RateLimitType.TOKEN_BUCKET;
    
    /**
     * 限流速率（每秒允许的请求数）
     */
    @Builder.Default
    private double rate = 100.0;
    
    /**
     * 令牌桶容量（最大突发请求数）
     */
    @Builder.Default
    private int capacity = 200;
    
    /**
     * 滑动窗口时间间隔（毫秒）
     */
    @Builder.Default
    private long windowSizeMs = 1000;
    
    /**
     * 滑动窗口分片数量
     */
    @Builder.Default
    private int windowSlices = 10;
    
    /**
     * 是否启用限流
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 限流键前缀
     */
    private String keyPrefix;
    
    /**
     * 创建默认配置
     */
    public static RateLimitConfig defaultConfig() {
        return RateLimitConfig.builder()
                .type(RateLimitType.TOKEN_BUCKET)
                .rate(100.0)
                .capacity(200)
                .enabled(true)
                .build();
    }
    
    /**
     * 创建高性能配置
     */
    public static RateLimitConfig highPerformanceConfig() {
        return RateLimitConfig.builder()
                .type(RateLimitType.TOKEN_BUCKET)
                .rate(1000.0)
                .capacity(2000)
                .enabled(true)
                .build();
    }
    
    /**
     * 创建严格限流配置
     */
    public static RateLimitConfig strictConfig() {
        return RateLimitConfig.builder()
                .type(RateLimitType.SLIDING_WINDOW)
                .rate(50.0)
                .capacity(100)
                .windowSizeMs(1000)
                .windowSlices(20)
                .enabled(true)
                .build();
    }
    
    /**
     * 验证配置有效性
     */
    public boolean isValid() {
        if (rate <= 0 || capacity <= 0) {
            return false;
        }
        
        if (type == RateLimitType.SLIDING_WINDOW) {
            return windowSizeMs > 0 && windowSlices > 0 && windowSlices <= 100;
        }
        
        return true;
    }
    
    /**
     * 获取配置描述
     */
    public String getDescription() {
        return String.format("%s限流 - 速率:%.1f/s, 容量:%d, 启用:%s", 
                type.getDescription(), rate, capacity, enabled);
    }
}
