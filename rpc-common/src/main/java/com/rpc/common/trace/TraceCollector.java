package com.rpc.common.trace;

/**
 * 追踪数据收集器接口
 */
public interface TraceCollector {
    
    /**
     * 收集追踪数据
     * @param context 追踪上下文
     */
    void collect(TraceContext context);
}
