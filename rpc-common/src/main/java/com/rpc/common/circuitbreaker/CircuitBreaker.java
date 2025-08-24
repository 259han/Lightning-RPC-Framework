package com.rpc.common.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器实现
 */
@Slf4j
public class CircuitBreaker {
    
    /**
     * 熔断器状态
     */
    public enum State {
        CLOSED,    // 关闭状态：正常处理请求
        OPEN,      // 开启状态：拒绝所有请求
        HALF_OPEN  // 半开状态：允许少量请求测试服务是否恢复
    }
    
    private final String serviceName;
    private final int failureThreshold;           // 失败阈值
    private final long recoveryTimeout;           // 恢复超时时间
    private final int halfOpenMaxCalls;           // 半开状态最大调用数
    
    private final AtomicReference<State> state;   // 当前状态
    private final AtomicInteger failureCount;    // 失败计数
    private final AtomicInteger successCount;    // 成功计数
    private final AtomicLong lastFailureTime;    // 最后失败时间
    private final AtomicInteger halfOpenCalls;   // 半开状态调用计数
    
    public CircuitBreaker(String serviceName, int failureThreshold, long recoveryTimeout) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.recoveryTimeout = recoveryTimeout;
        this.halfOpenMaxCalls = 3; // 半开状态最多允许3次调用
        
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.halfOpenCalls = new AtomicInteger(0);
    }
    
    /**
     * 检查是否允许调用
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // 检查是否到了恢复时间
                if (System.currentTimeMillis() - lastFailureTime.get() > recoveryTimeout) {
                    // 尝试转换到半开状态
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        halfOpenCalls.set(0);
                        log.info("熔断器 [{}] 从OPEN转换到HALF_OPEN状态", serviceName);
                        return true;
                    }
                }
                return false;
                
            case HALF_OPEN:
                // 半开状态允许少量请求
                return halfOpenCalls.get() < halfOpenMaxCalls;
                
            default:
                return false;
        }
    }
    
    /**
     * 记录成功调用
     */
    public void recordSuccess() {
        State currentState = state.get();
        successCount.incrementAndGet();
        
        if (currentState == State.HALF_OPEN) {
            halfOpenCalls.incrementAndGet();
            
            // 半开状态下如果连续成功，转换到关闭状态
            if (halfOpenCalls.get() >= halfOpenMaxCalls) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    reset();
                    log.info("熔断器 [{}] 从HALF_OPEN转换到CLOSED状态", serviceName);
                }
            }
        } else if (currentState == State.CLOSED) {
            // 关闭状态下成功调用重置失败计数
            failureCount.set(0);
        }
    }
    
    /**
     * 记录失败调用
     */
    public void recordFailure() {
        State currentState = state.get();
        int currentFailures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (currentState == State.CLOSED) {
            // 关闭状态下失败次数达到阈值，转换到开启状态
            if (currentFailures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    log.warn("熔断器 [{}] 从CLOSED转换到OPEN状态，失败次数: {}", serviceName, currentFailures);
                }
            }
        } else if (currentState == State.HALF_OPEN) {
            // 半开状态下失败，直接转换到开启状态
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                log.warn("熔断器 [{}] 从HALF_OPEN转换到OPEN状态", serviceName);
            }
        }
    }
    
    /**
     * 重置熔断器状态
     */
    private void reset() {
        failureCount.set(0);
        successCount.set(0);
        halfOpenCalls.set(0);
        lastFailureTime.set(0);
    }
    
    /**
     * 获取当前状态
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * 获取失败计数
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * 获取成功计数
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * 获取服务名
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * 强制重置熔断器（用于管理接口）
     */
    public void forceReset() {
        state.set(State.CLOSED);
        reset();
        log.info("熔断器 [{}] 被强制重置", serviceName);
    }
    
    /**
     * 强制打开熔断器（用于管理接口）
     */
    public void forceOpen() {
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
        log.info("熔断器 [{}] 被强制打开", serviceName);
    }
}
