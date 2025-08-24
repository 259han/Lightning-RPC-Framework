package com.rpc.client;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * RPC请求传输接口
 */
public interface RpcRequestTransport {
    /**
     * 发送RPC请求
     *
     * @param request RPC请求
     * @return 响应的CompletableFuture
     */
    CompletableFuture<RpcResponse<Object>> sendRequest(RpcRequest request);
    
    /**
     * 关闭传输
     */
    default void close() {
        // 默认实现为空
    }
}
