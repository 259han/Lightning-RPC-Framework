package com.rpc.common.trace;

import lombok.Data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪上下文
 */
@Data
public class TraceContext {
    
    /**
     * 追踪ID - 标识一次完整的请求链路
     */
    private String traceId;
    
    /**
     * 跨度ID - 标识当前服务调用
     */
    private String spanId;
    
    /**
     * 父跨度ID - 标识调用方的跨度
     */
    private String parentSpanId;
    
    /**
     * 开始时间
     */
    private long startTime;
    
    /**
     * 结束时间
     */
    private long endTime;
    
    /**
     * 服务名
     */
    private String serviceName;
    
    /**
     * 方法名
     */
    private String methodName;
    
    /**
     * 调用结果状态
     */
    private TraceStatus status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 标签信息
     */
    private Map<String, String> tags = new ConcurrentHashMap<>();
    
    /**
     * 日志信息
     */
    private Map<String, Object> logs = new ConcurrentHashMap<>();
    
    public TraceContext() {
        this.traceId = generateId();
        this.spanId = generateId();
        this.startTime = System.currentTimeMillis();
        this.status = TraceStatus.STARTED;
    }
    
    public TraceContext(String traceId, String parentSpanId) {
        this.traceId = traceId;
        this.spanId = generateId();
        this.parentSpanId = parentSpanId;
        this.startTime = System.currentTimeMillis();
        this.status = TraceStatus.STARTED;
    }
    
    /**
     * 生成唯一ID
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 标记开始
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
        this.status = TraceStatus.STARTED;
    }
    
    /**
     * 标记成功完成
     */
    public void finish() {
        this.endTime = System.currentTimeMillis();
        this.status = TraceStatus.SUCCESS;
    }
    
    /**
     * 标记失败完成
     */
    public void finishWithError(String errorMessage) {
        this.endTime = System.currentTimeMillis();
        this.status = TraceStatus.ERROR;
        this.errorMessage = errorMessage;
    }
    
    /**
     * 添加标签
     */
    public void addTag(String key, String value) {
        tags.put(key, value);
    }
    
    /**
     * 添加日志
     */
    public void addLog(String key, Object value) {
        logs.put(key, value);
    }
    
    /**
     * 获取耗时（毫秒）
     */
    public long getDuration() {
        if (endTime > 0) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 创建子跨度
     */
    public TraceContext createChildSpan() {
        return new TraceContext(this.traceId, this.spanId);
    }
    
    /**
     * 追踪状态枚举
     */
    public enum TraceStatus {
        STARTED,    // 已开始
        SUCCESS,    // 成功
        ERROR       // 失败
    }
}
