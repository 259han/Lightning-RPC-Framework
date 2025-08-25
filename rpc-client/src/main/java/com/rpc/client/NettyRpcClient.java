package com.rpc.client;

import com.rpc.client.pool.ConnectionPoolManager;
import com.rpc.client.pool.PooledConnection;
import com.rpc.common.config.ConnectionPoolConfig;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Netty的RPC客户端 - 增强版，支持连接池和请求超时清理
 */
@Slf4j
public class NettyRpcClient implements RpcRequestTransport {
    private final ServiceDiscovery serviceDiscovery;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ConnectionPoolManager connectionPoolManager;
    private final Map<Long, CompletableFuture<RpcResponse<Object>>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<Long, Long> requestTimeoutMap = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong(0);
    private final ScheduledExecutorService timeoutExecutor;
    private final ConnectionPoolConfig poolConfig;
    
    // 保持向后兼容的简单连接缓存（当连接池禁用时使用）
    private final Map<String, Channel> channelMap = new ConcurrentHashMap<>();
    
    // 兼容旧的构造函数
    public NettyRpcClient(ServiceDiscovery serviceDiscovery) {
        this(serviceDiscovery, ConnectionPoolConfig.defaultConfig());
    }
    
    // 新的构造函数，支持连接池配置
    public NettyRpcClient(ServiceDiscovery serviceDiscovery, ConnectionPoolConfig poolConfig) {
        this.serviceDiscovery = serviceDiscovery;
        this.poolConfig = poolConfig;
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        
        // 配置Bootstrap
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) poolConfig.getConnectTimeout())
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
        
        // 初始化连接池管理器
        if (poolConfig.isEnabled()) {
            this.connectionPoolManager = new ConnectionPoolManager(poolConfig, bootstrap);
            log.info("连接池已启用，配置: {}", poolConfig);
        } else {
            this.connectionPoolManager = null;
            log.info("连接池已禁用，使用简单连接缓存");
        }
        
        // 初始化超时清理执行器
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NettyRpcClient-TimeoutCleaner");
            t.setDaemon(true);
            return t;
        });
        
        // 启动请求超时清理任务
        startRequestTimeoutCleaner();
        
        log.info("NettyRpcClient初始化完成");
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
            
            // 保存请求和超时信息
            pendingRequests.put(requestId, resultFuture);
            requestTimeoutMap.put(requestId, System.currentTimeMillis());
            
            // 获取服务地址
            InetSocketAddress serverAddress = serviceDiscovery.selectServiceAddress(request);
            if (serverAddress == null) {
                throw new RuntimeException("找不到服务地址: " + request.getRpcServiceName());
            }
            
            // 根据是否启用连接池选择不同的发送方式
            if (connectionPoolManager != null) {
                sendRequestWithConnectionPool(rpcMessage, serverAddress, resultFuture, requestId);
            } else {
                sendRequestWithSimpleCache(rpcMessage, serverAddress, resultFuture, requestId);
            }
            
        } catch (Exception e) {
            // 发生异常，清理请求
            cleanupRequest(requestId);
            resultFuture.completeExceptionally(e);
            log.error("发送请求异常", e);
        }
        
        return resultFuture;
    }
    
    /**
     * 使用连接池发送请求
     */
    private void sendRequestWithConnectionPool(RpcMessage rpcMessage, InetSocketAddress serverAddress, 
                                             CompletableFuture<RpcResponse<Object>> resultFuture, long requestId) {
        connectionPoolManager.getConnection(serverAddress)
            .thenAccept(pooledConnection -> {
                try {
                    Channel channel = pooledConnection.getChannel();
                    if (!channel.isActive()) {
                        pooledConnection.close();
                        resultFuture.completeExceptionally(new RuntimeException("连接不可用"));
                        cleanupRequest(requestId);
                        return;
                    }
                    
                    // 发送请求
                    channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
                        // 无论成功失败都要归还连接
                        pooledConnection.returnToPool();
                        
                        if (future.isSuccess()) {
                            log.debug("客户端发送消息成功: {}", rpcMessage.getRequestId());
                        } else {
                            cleanupRequest(requestId);
                            resultFuture.completeExceptionally(future.cause());
                            log.error("客户端发送消息失败", future.cause());
                        }
                    });
                } catch (Exception e) {
                    pooledConnection.close();
                    cleanupRequest(requestId);
                    resultFuture.completeExceptionally(e);
                }
            })
            .exceptionally(throwable -> {
                cleanupRequest(requestId);
                resultFuture.completeExceptionally(throwable);
                log.error("获取连接失败: {}", serverAddress, throwable);
                return null;
            });
    }
    
    /**
     * 使用简单缓存发送请求（向后兼容）
     */
    private void sendRequestWithSimpleCache(RpcMessage rpcMessage, InetSocketAddress serverAddress,
                                          CompletableFuture<RpcResponse<Object>> resultFuture, long requestId) {
        try {
            // 获取或创建Channel
            Channel channel = getChannel(serverAddress);
            if (!channel.isActive()) {
                cleanupRequest(requestId);
                resultFuture.completeExceptionally(new RuntimeException("连接不可用"));
                return;
            }
            
            // 发送请求
            channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.debug("客户端发送消息成功: {}", rpcMessage.getRequestId());
                } else {
                    future.channel().close();
                    cleanupRequest(requestId);
                    resultFuture.completeExceptionally(future.cause());
                    log.error("客户端发送消息失败", future.cause());
                }
            });
        } catch (Exception e) {
            cleanupRequest(requestId);
            resultFuture.completeExceptionally(e);
            log.error("简单缓存发送请求异常", e);
        }
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
     * 清理请求相关资源
     */
    private void cleanupRequest(long requestId) {
        pendingRequests.remove(requestId);
        requestTimeoutMap.remove(requestId);
    }
    
    /**
     * 启动请求超时清理任务
     */
    private void startRequestTimeoutCleaner() {
        timeoutExecutor.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();
                long timeoutThreshold = poolConfig.getRequestTimeoutCheckInterval();
                
                requestTimeoutMap.entrySet().removeIf(entry -> {
                    long requestId = entry.getKey();
                    long requestTime = entry.getValue();
                    
                    if (now - requestTime > timeoutThreshold) {
                        CompletableFuture<RpcResponse<Object>> future = pendingRequests.remove(requestId);
                        if (future != null && !future.isDone()) {
                            future.completeExceptionally(new RuntimeException("请求超时被清理: " + requestId));
                            log.warn("清理超时请求: {} (超时时间: {}ms)", requestId, now - requestTime);
                        }
                        return true;
                    }
                    return false;
                });
                
            } catch (Exception e) {
                log.warn("请求超时清理异常", e);
            }
        }, poolConfig.getRequestTimeoutCheckInterval(), 
           poolConfig.getRequestTimeoutCheckInterval(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 获取连接池统计信息
     */
    public String getConnectionPoolStats() {
        if (connectionPoolManager != null) {
            return connectionPoolManager.getOverallStats().toString();
        } else {
            return String.format("SimpleCache{activeConnections=%d}", channelMap.size());
        }
    }
    
    /**
     * 获取当前等待响应的请求数
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        log.info("开始关闭NettyRpcClient...");
        
        // 关闭超时清理任务
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdown();
        }
        
        // 取消所有等待中的请求
        pendingRequests.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new RuntimeException("客户端关闭"));
            }
        });
        pendingRequests.clear();
        requestTimeoutMap.clear();
        
        // 关闭连接池
        if (connectionPoolManager != null) {
            connectionPoolManager.close();
        }
        
        // 关闭简单连接缓存
        for (Channel channel : channelMap.values()) {
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        }
        channelMap.clear();
        
        // 关闭事件循环组
        eventLoopGroup.shutdownGracefully();
        
        log.info("NettyRpcClient已关闭");
    }
}
