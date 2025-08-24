package com.rpc.client;

import com.rpc.common.constants.RpcConstants;
import com.rpc.protocol.RpcMessage;
import com.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Netty RPC客户端处理器
 */
@Slf4j
public class NettyRpcClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final Map<Long, CompletableFuture<RpcResponse<Object>>> pendingRequests;
    
    public NettyRpcClientHandler(Map<Long, CompletableFuture<RpcResponse<Object>>> pendingRequests) {
        this.pendingRequests = pendingRequests;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        // 处理响应消息
        if (msg.getMessageType() == RpcConstants.RESPONSE_TYPE) {
            long requestId = msg.getRequestId();
            log.info("客户端收到响应: {}", requestId);
            
            // 获取并移除等待的请求
            CompletableFuture<RpcResponse<Object>> future = pendingRequests.remove(requestId);
            if (future != null) {
                @SuppressWarnings("unchecked")
                RpcResponse<Object> response = (RpcResponse<Object>) msg.getData();
                future.complete(response);
            } else {
                log.warn("收到未知请求ID的响应: {}", requestId);
            }
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}
