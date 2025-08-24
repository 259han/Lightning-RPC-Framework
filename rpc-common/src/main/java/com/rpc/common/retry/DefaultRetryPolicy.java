package com.rpc.common.retry;

import lombok.extern.slf4j.Slf4j;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * 默认重试策略 - 支持固定延迟和指数退避两种模式
 */
@Slf4j
public class DefaultRetryPolicy implements RetryPolicy {
    
    public enum DelayMode {
        FIXED,          // 固定延迟
        EXPONENTIAL     // 指数退避
    }
    
    private final int maxRetries;
    private final long baseDelay;
    private final DelayMode delayMode;
    private final double multiplier;
    private final long maxDelay;
    
    /**
     * 构造函数
     * @param maxRetries 最大重试次数
     * @param baseDelay 基础延迟时间（毫秒）
     * @param delayMode 延迟模式
     * @param multiplier 指数退避倍数（仅在EXPONENTIAL模式下有效）
     * @param maxDelay 最大延迟时间（毫秒）
     */
    public DefaultRetryPolicy(int maxRetries, long baseDelay, DelayMode delayMode, 
                            double multiplier, long maxDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
        this.delayMode = delayMode;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
    }
    
    /**
     * 固定延迟策略
     */
    public static DefaultRetryPolicy fixedDelay(int maxRetries, long delay) {
        return new DefaultRetryPolicy(maxRetries, delay, DelayMode.FIXED, 0, delay);
    }
    
    /**
     * 指数退避策略
     */
    public static DefaultRetryPolicy exponentialBackoff(int maxRetries, long baseDelay, 
                                                       double multiplier, long maxDelay) {
        return new DefaultRetryPolicy(maxRetries, baseDelay, DelayMode.EXPONENTIAL, 
                                    multiplier, maxDelay);
    }
    
    /**
     * 默认配置：最多重试3次，固定延迟1秒
     */
    public DefaultRetryPolicy() {
        this(3, 1000, DelayMode.FIXED, 0, 1000);
    }
    
    @Override
    public boolean shouldRetry(int retryCount, Throwable exception) {
        if (retryCount >= maxRetries) {
            log.debug("已达到最大重试次数 {}", maxRetries);
            return false;
        }
        
        boolean retriable = isRetriableException(exception);
        if (retriable) {
            log.debug("异常可重试: {}", exception.getClass().getSimpleName());
        } else {
            log.debug("不可重试的异常: {}", exception.getClass().getSimpleName());
        }
        
        return retriable;
    }
    
    @Override
    public long getRetryDelay(int retryCount) {
        if (delayMode == DelayMode.FIXED) {
            return baseDelay;
        } else {
            // 指数退避计算：baseDelay * multiplier^retryCount
            long delay = (long) (baseDelay * Math.pow(multiplier, retryCount));
            return Math.min(delay, maxDelay);
        }
    }
    
    @Override
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 判断异常是否可重试
     */
    private boolean isRetriableException(Throwable exception) {
        if (exception == null) {
            return false;
        }
        
        // 网络连接异常可重试
        if (exception instanceof ConnectException) {
            return true;
        }
        
        // 超时异常可重试
        if (exception instanceof TimeoutException) {
            return true;
        }
        
        // 服务不可用异常可重试
        if (exception.getMessage() != null && 
            (exception.getMessage().contains("Connection refused") ||
             exception.getMessage().contains("Connection reset") ||
             exception.getMessage().contains("No route to host"))) {
            return true;
        }
        
        return false;
    }
}
