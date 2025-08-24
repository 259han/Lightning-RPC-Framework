package com.rpc.common.retry;

/**
 * 重试策略接口
 */
public interface RetryPolicy {
    
    /**
     * 是否应该重试
     * @param retryCount 当前重试次数
     * @param exception 异常信息
     * @return 是否重试
     */
    boolean shouldRetry(int retryCount, Throwable exception);
    
    /**
     * 获取重试延迟时间（毫秒）
     * @param retryCount 当前重试次数
     * @return 延迟时间
     */
    long getRetryDelay(int retryCount);
    
    /**
     * 获取最大重试次数
     * @return 最大重试次数
     */
    int getMaxRetries();
}
