package com.rpc.client;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 批量RPC调用接口
 * 
 * 支持批量请求以减少网络开销，提高并发性能
 */
public interface BatchRpcClient {
    
    /**
     * 批量执行RPC调用
     * 
     * @param requests 批量请求列表
     * @param <T> 返回值类型
     * @return 批量响应的Future
     */
    <T> CompletableFuture<List<RpcResponse<T>>> batchCall(List<RpcRequest> requests);
    
    /**
     * 异步执行单个RPC调用
     * 
     * @param request RPC请求
     * @param <T> 返回值类型
     * @return 响应的Future
     */
    <T> CompletableFuture<RpcResponse<T>> asyncCall(RpcRequest request);
    
    /**
     * 并行执行多个RPC调用
     * 
     * @param requests 请求列表
     * @param maxConcurrency 最大并发数
     * @param <T> 返回值类型
     * @return 响应列表的Future
     */
    <T> CompletableFuture<List<RpcResponse<T>>> parallelCall(List<RpcRequest> requests, int maxConcurrency);
    
    /**
     * 获取当前等待中的异步调用数量
     * 
     * @return 等待中的调用数量
     */
    int getPendingAsyncCalls();
    
    /**
     * 取消所有等待中的异步调用
     */
    void cancelAllPendingCalls();
}
