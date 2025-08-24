package com.rpc.client;

import com.rpc.common.constants.RpcConstants;
import com.rpc.common.registry.ServiceDiscovery;
import com.rpc.protocol.RpcMessage;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.protocol.codec.RpcMessageDecoder;
import com.rpc.protocol.codec.RpcMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Netty的RPC客户端
 */
@Slf4j
public class NettyRpcClient implements RpcRequestTransport {
    private final ServiceDiscovery serviceDiscovery;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<RpcResponse<Object>>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    
    public NettyRpcClient(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RpcMessageDecoder());
                        ch.pipeline().addLast(new RpcMessageEncoder());
                        ch.pipeline().addLast(new NettyRpcClientHandler(pendingRequests));
                    }
                });
    }
    
    @Override
    public CompletableFuture<RpcResponse<Object>> sendRequest(RpcRequest request) {
        // 创建返回的CompletableFuture
        CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();
        
        // 生成请求ID
        long requestId = requestIdGenerator.incrementAndGet();
        
        try {
            // 构建RPC消息
            RpcMessage rpcMessage = new RpcMessage();
            rpcMessage.setRequestId(requestId);
            rpcMessage.setMessageType(RpcConstants.REQUEST_TYPE);
            rpcMessage.setCodecType(RpcConstants.SERIALIZATION_JSON);
            rpcMessage.setCompressType(RpcConstants.COMPRESS_TYPE_NONE);
            rpcMessage.setData(request);
            
            // 保存请求
            pendingRequests.put(requestId, resultFuture);
            
            // 获取服务地址
            InetSocketAddress serverAddress = serviceDiscovery.selectServiceAddress(request);
            if (serverAddress == null) {
                throw new RuntimeException("找不到服务地址: " + request.getRpcServiceName());
            }
            
            // 获取或创建Channel
            Channel channel = getChannel(serverAddress);
            if (!channel.isActive()) {
                eventLoopGroup.shutdownGracefully();
                return resultFuture;
            }
            
            // 发送请求
            channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("客户端发送消息成功: {}", rpcMessage.getRequestId());
                } else {
                    future.channel().close();
                    resultFuture.completeExceptionally(future.cause());
                    log.error("客户端发送消息失败", future.cause());
                }
            });
        } catch (Exception e) {
            // 发生异常，移除请求
            pendingRequests.remove(requestId);
            resultFuture.completeExceptionally(e);
            log.error("发送请求异常", e);
        }
        
        return resultFuture;
    }
    
    /**
     * 获取Channel（带连接池管理）
     *
     * @param inetSocketAddress 服务器地址
     * @return Channel
     */
    private Channel getChannel(InetSocketAddress inetSocketAddress) {
        String key = inetSocketAddress.toString();
        
        // 双重检查锁定，确保线程安全
        Channel channel = channelMap.get(key);
        if (channel != null && channel.isActive()) {
            return channel;
        }
        
        synchronized (this) {
            // 再次检查，避免重复创建
            channel = channelMap.get(key);
            if (channel != null && channel.isActive()) {
                return channel;
            }
            
            // 移除无效连接
            if (channel != null) {
                channelMap.remove(key);
                if (channel.isOpen()) {
                    channel.close();
                }
            }
            
            // 创建新的Channel
            try {
                ChannelFuture future = bootstrap.connect(inetSocketAddress);
                channel = future.sync().channel();
                
                // 添加连接关闭监听器，自动清理无效连接
                channel.closeFuture().addListener(f -> {
                    channelMap.remove(key);
                    log.info("连接已关闭，从连接池移除: {}", key);
                });
                
                channelMap.put(key, channel);
                log.info("成功创建新连接: {}", key);
                return channel;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("连接服务器失败: {}", inetSocketAddress, e);
                throw new RuntimeException("连接服务器失败", e);
            }
        }
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        eventLoopGroup.shutdownGracefully();
        for (Channel channel : channelMap.values()) {
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        }
    }
}
