package com.rpc.server.interceptor;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;

/**
 * RPC拦截器接口
 * 
 * 用于在RPC调用前后执行额外的逻辑，如：
 * - 安全认证
 * - 限流控制
 * - 日志记录
 * - 性能监控
 * - 参数验证
 */
public interface RpcInterceptor {
    
    /**
     * 前置处理
     * 
     * @param request RPC请求
     * @param response RPC响应
     * @return true 继续处理，false 中断处理
     */
    boolean preProcess(RpcRequest request, RpcResponse<?> response);
    
    /**
     * 后置处理
     * 
     * @param request RPC请求
     * @param response RPC响应
     */
    default void postProcess(RpcRequest request, RpcResponse<?> response) {
        // 默认空实现
    }
    
    /**
     * 异常处理
     * 
     * @param request RPC请求
     * @param response RPC响应
     * @param throwable 异常
     */
    default void onException(RpcRequest request, RpcResponse<?> response, Throwable throwable) {
        // 默认空实现
    }
    
    /**
     * 获取拦截器优先级
     * 数值越小优先级越高
     * 
     * @return 优先级
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 获取拦截器名称
     * 
     * @return 拦截器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
