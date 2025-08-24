package com.rpc.common.trace;

import lombok.extern.slf4j.Slf4j;

/**
 * 日志追踪收集器
 */
@Slf4j
public class LogTraceCollector implements TraceCollector {
    
    @Override
    public void collect(TraceContext context) {
        if (context.getStatus() == TraceContext.TraceStatus.ERROR) {
            log.error("Trace[{}] Span[{}] {}#{} 执行失败, 耗时: {}ms, 错误: {}", 
                    context.getTraceId(),
                    context.getSpanId(),
                    context.getServiceName(),
                    context.getMethodName(),
                    context.getDuration(),
                    context.getErrorMessage()
            );
        } else {
            log.info("Trace[{}] Span[{}] {}#{} 执行完成, 耗时: {}ms", 
                    context.getTraceId(),
                    context.getSpanId(),
                    context.getServiceName(),
                    context.getMethodName(),
                    context.getDuration()
            );
        }
        
        // 输出详细信息到DEBUG级别
        if (log.isDebugEnabled()) {
            log.debug("追踪详情: {}", formatTraceDetails(context));
        }
    }
    
    /**
     * 格式化追踪详情
     */
    private String formatTraceDetails(TraceContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  TraceId: ").append(context.getTraceId());
        sb.append("\n  SpanId: ").append(context.getSpanId());
        if (context.getParentSpanId() != null) {
            sb.append("\n  ParentSpanId: ").append(context.getParentSpanId());
        }
        sb.append("\n  Service: ").append(context.getServiceName());
        sb.append("\n  Method: ").append(context.getMethodName());
        sb.append("\n  Duration: ").append(context.getDuration()).append("ms");
        sb.append("\n  Status: ").append(context.getStatus());
        
        if (context.getErrorMessage() != null) {
            sb.append("\n  Error: ").append(context.getErrorMessage());
        }
        
        if (!context.getTags().isEmpty()) {
            sb.append("\n  Tags: ").append(context.getTags());
        }
        
        if (!context.getLogs().isEmpty()) {
            sb.append("\n  Logs: ").append(context.getLogs());
        }
        
        return sb.toString();
    }
}
