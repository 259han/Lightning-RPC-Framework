package com.rpc.common.trace;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 链路追踪管理器
 */
@Slf4j
public class TraceManager {
    
    private static final TraceManager INSTANCE = new TraceManager();
    private static final ThreadLocal<TraceContext> CURRENT_TRACE = new ThreadLocal<>();
    
    private final ConcurrentMap<String, List<TraceContext>> traces = new ConcurrentHashMap<>();
    private final List<TraceCollector> collectors = new ArrayList<>();
    
    private TraceManager() {}
    
    public static TraceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 开始一个新的追踪
     */
    public TraceContext startTrace(String serviceName, String methodName) {
        TraceContext context = new TraceContext();
        context.setServiceName(serviceName);
        context.setMethodName(methodName);
        context.addTag("service.name", serviceName);
        context.addTag("method.name", methodName);
        
        CURRENT_TRACE.set(context);
        return context;
    }
    
    /**
     * 开始一个子追踪
     */
    public TraceContext startChildTrace(String serviceName, String methodName) {
        TraceContext parentContext = CURRENT_TRACE.get();
        TraceContext childContext;
        
        if (parentContext != null) {
            childContext = parentContext.createChildSpan();
        } else {
            childContext = new TraceContext();
        }
        
        childContext.setServiceName(serviceName);
        childContext.setMethodName(methodName);
        childContext.addTag("service.name", serviceName);
        childContext.addTag("method.name", methodName);
        
        CURRENT_TRACE.set(childContext);
        return childContext;
    }
    
    /**
     * 开始带有追踪信息的追踪（用于RPC调用接收端）
     */
    public TraceContext startTrace(String traceId, String parentSpanId, String serviceName, String methodName) {
        TraceContext context = new TraceContext(traceId, parentSpanId);
        context.setServiceName(serviceName);
        context.setMethodName(methodName);
        context.addTag("service.name", serviceName);
        context.addTag("method.name", methodName);
        
        CURRENT_TRACE.set(context);
        return context;
    }
    
    /**
     * 获取当前追踪上下文
     */
    public TraceContext getCurrentTrace() {
        return CURRENT_TRACE.get();
    }
    
    /**
     * 完成当前追踪
     */
    public void finishTrace() {
        TraceContext context = CURRENT_TRACE.get();
        if (context != null) {
            context.finish();
            collectTrace(context);
            CURRENT_TRACE.remove();
        }
    }
    
    /**
     * 完成当前追踪（带错误）
     */
    public void finishTraceWithError(String errorMessage) {
        TraceContext context = CURRENT_TRACE.get();
        if (context != null) {
            context.finishWithError(errorMessage);
            collectTrace(context);
            CURRENT_TRACE.remove();
        }
    }
    
    /**
     * 完成当前追踪（带异常）
     */
    public void finishTraceWithError(Throwable throwable) {
        String errorMessage = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        finishTraceWithError(errorMessage);
    }
    
    /**
     * 添加标签到当前追踪
     */
    public void addTag(String key, String value) {
        TraceContext context = CURRENT_TRACE.get();
        if (context != null) {
            context.addTag(key, value);
        }
    }
    
    /**
     * 添加日志到当前追踪
     */
    public void addLog(String key, Object value) {
        TraceContext context = CURRENT_TRACE.get();
        if (context != null) {
            context.addLog(key, value);
        }
    }
    
    /**
     * 收集追踪数据
     */
    private void collectTrace(TraceContext context) {
        // 添加到内存存储（用于查询）
        traces.computeIfAbsent(context.getTraceId(), k -> new ArrayList<>()).add(context);
        
        // 通知所有收集器
        for (TraceCollector collector : collectors) {
            try {
                collector.collect(context);
            } catch (Exception e) {
                log.error("追踪数据收集失败", e);
            }
        }
    }
    
    /**
     * 添加追踪收集器
     */
    public void addCollector(TraceCollector collector) {
        collectors.add(collector);
        log.info("添加追踪收集器: {}", collector.getClass().getSimpleName());
    }
    
    /**
     * 移除追踪收集器
     */
    public void removeCollector(TraceCollector collector) {
        collectors.remove(collector);
        log.info("移除追踪收集器: {}", collector.getClass().getSimpleName());
    }
    
    /**
     * 获取追踪链路
     */
    public List<TraceContext> getTraceChain(String traceId) {
        return traces.getOrDefault(traceId, new ArrayList<>());
    }
    
    /**
     * 清理过期的追踪数据
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        long expireTime = 24 * 60 * 60 * 1000; // 24小时
        
        traces.entrySet().removeIf(entry -> {
            List<TraceContext> contexts = entry.getValue();
            if (!contexts.isEmpty()) {
                TraceContext first = contexts.get(0);
                return (currentTime - first.getStartTime()) > expireTime;
            }
            return true;
        });
        
        log.debug("清理过期追踪数据，当前追踪数量: {}", traces.size());
    }
    
    /**
     * 清理所有追踪数据
     */
    public void clear() {
        traces.clear();
        CURRENT_TRACE.remove();
        log.info("清理所有追踪数据");
    }
}
