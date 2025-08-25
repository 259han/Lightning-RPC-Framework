package com.rpc.client.pool;

import com.rpc.common.config.ConnectionPoolConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.bootstrap.Bootstrap;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高级连接池实现
 * 
 * 特性：
 * - 连接数限制和排队管理
 * - 连接健康检查和自动恢复
 * - 连接空闲超时和清理
 * - 连接负载均衡分配
 * - 详细的连接池统计信息
 */
@Slf4j
public class ConnectionPool {
    
    private final InetSocketAddress serverAddress;
    private final ConnectionPoolConfig config;
    private final Bootstrap bootstrap;
    
    // 连接池核心数据结构
    private final BlockingQueue<PooledConnection> availableConnections;
    private final ConcurrentMap<String, PooledConnection> allConnections;
    private final BlockingQueue<CompletableFuture<PooledConnection>> waitingRequests;
    
    // 统计信息
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicLong connectionCreateCount = new AtomicLong(0);
    private final AtomicLong connectionCloseCount = new AtomicLong(0);
    private final AtomicLong requestWaitCount = new AtomicLong(0);
    
    // 后台任务
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;
    
    public ConnectionPool(InetSocketAddress serverAddress, 
                         ConnectionPoolConfig config, 
                         Bootstrap bootstrap) {
        this.serverAddress = serverAddress;
        this.config = config;
        this.bootstrap = bootstrap;
        
        this.availableConnections = new LinkedBlockingQueue<>();
        this.allConnections = new ConcurrentHashMap<>();
        this.waitingRequests = new LinkedBlockingQueue<>();
        
        // 创建后台任务调度器
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ConnectionPool-" + serverAddress + "-Worker");
            t.setDaemon(true);
            return t;
        });
        
        // 启动后台任务
        startBackgroundTasks();
        
        // 预热连接
        warmupConnections();
        
        log.info("连接池已初始化: {} (最大连接数: {})", serverAddress, config.getMaxConnectionsPerServer());
    }
    
    /**
     * 获取连接
     */
    public CompletableFuture<PooledConnection> getConnection() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("连接池已关闭"));
        }
        
        // 尝试从可用连接中获取
        PooledConnection connection = availableConnections.poll();
        if (connection != null && connection.isHealthy()) {
            connection.markInUse();
            return CompletableFuture.completedFuture(connection);
        }
        
        // 如果有无效连接，清理它
        if (connection != null) {
            removeConnection(connection);
        }
        
        // 检查是否可以创建新连接
        if (totalConnections.get() < config.getMaxConnectionsPerServer()) {
            return createNewConnection();
        }
        
        // 连接池已满，加入等待队列
        CompletableFuture<PooledConnection> future = new CompletableFuture<>();
        
        if (waitingRequests.size() >= config.getMaxPendingRequests()) {
            future.completeExceptionally(new RuntimeException("连接池等待队列已满"));
            return future;
        }
        
        waitingRequests.offer(future);
        requestWaitCount.incrementAndGet();
        
        log.debug("连接池已满，请求加入等待队列: {} (等待数: {})", 
                serverAddress, waitingRequests.size());
        
        return future;
    }
    
    /**
     * 归还连接
     */
    public void returnConnection(PooledConnection connection) {
        if (closed || !connection.isHealthy()) {
            removeConnection(connection);
            return;
        }
        
        connection.markAvailable();
        
        // 优先满足等待中的请求
        CompletableFuture<PooledConnection> waitingRequest = waitingRequests.poll();
        if (waitingRequest != null && !waitingRequest.isDone()) {
            connection.markInUse();
            waitingRequest.complete(connection);
            return;
        }
        
        // 没有等待请求，放回可用连接池
        availableConnections.offer(connection);
    }
    
    /**
     * 创建新连接
     */
    private CompletableFuture<PooledConnection> createNewConnection() {
        CompletableFuture<PooledConnection> future = new CompletableFuture<>();
        
        totalConnections.incrementAndGet();
        connectionCreateCount.incrementAndGet();
        
        ChannelFuture channelFuture = bootstrap.connect(serverAddress);
        channelFuture.addListener(f -> {
            if (f.isSuccess()) {
                Channel channel = channelFuture.channel();
                String connectionId = generateConnectionId();
                PooledConnection pooledConnection = new PooledConnection(
                    connectionId, channel, this, System.currentTimeMillis()
                );
                
                allConnections.put(connectionId, pooledConnection);
                activeConnections.incrementAndGet();
                
                // 设置连接关闭监听器
                channel.closeFuture().addListener(closeFuture -> {
                    removeConnection(pooledConnection);
                });
                
                pooledConnection.markInUse();
                future.complete(pooledConnection);
                
                log.debug("成功创建新连接: {} -> {}", connectionId, serverAddress);
            } else {
                totalConnections.decrementAndGet();
                future.completeExceptionally(f.cause());
                log.error("创建连接失败: {}", serverAddress, f.cause());
            }
        });
        
        return future;
    }
    
    /**
     * 移除连接
     */
    private void removeConnection(PooledConnection connection) {
        allConnections.remove(connection.getId());
        availableConnections.remove(connection);
        
        if (connection.getChannel().isOpen()) {
            connection.getChannel().close();
        }
        
        activeConnections.decrementAndGet();
        totalConnections.decrementAndGet();
        connectionCloseCount.incrementAndGet();
        
        log.debug("连接已移除: {} -> {}", connection.getId(), serverAddress);
    }
    
    /**
     * 预热连接
     */
    private void warmupConnections() {
        int warmupCount = Math.min(config.getWarmupConnections(), config.getMaxConnectionsPerServer());
        
        for (int i = 0; i < warmupCount; i++) {
            createNewConnection().thenAccept(connection -> {
                returnConnection(connection);
                log.debug("预热连接完成: {}", connection.getId());
            }).exceptionally(throwable -> {
                log.warn("预热连接失败: {}", serverAddress, throwable);
                return null;
            });
        }
    }
    
    /**
     * 启动后台任务
     */
    private void startBackgroundTasks() {
        // 健康检查任务
        if (config.isHealthCheckEnabled()) {
            scheduler.scheduleWithFixedDelay(
                this::performHealthCheck,
                config.getHealthCheckInterval(),
                config.getHealthCheckInterval(),
                TimeUnit.MILLISECONDS
            );
        }
        
        // 空闲连接清理任务
        scheduler.scheduleWithFixedDelay(
            this::cleanupIdleConnections,
            config.getConnectionIdleTimeout(),
            config.getConnectionIdleTimeout() / 3,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 健康检查
     */
    private void performHealthCheck() {
        if (closed) return;
        
        allConnections.values().forEach(connection -> {
            if (!connection.isHealthy()) {
                log.warn("发现不健康连接，准备移除: {}", connection.getId());
                removeConnection(connection);
            }
        });
        
        // 如果活跃连接数不足，创建新连接
        int currentActive = activeConnections.get();
        int minConnections = Math.min(2, config.getMaxConnectionsPerServer());
        
        if (currentActive < minConnections && totalConnections.get() < config.getMaxConnectionsPerServer()) {
            createNewConnection().thenAccept(this::returnConnection);
        }
    }
    
    /**
     * 清理空闲连接
     */
    private void cleanupIdleConnections() {
        if (closed) return;
        
        long now = System.currentTimeMillis();
        long idleTimeout = config.getConnectionIdleTimeout();
        
        allConnections.values().forEach(connection -> {
            if (connection.isAvailable() && 
                (now - connection.getLastUsedTime()) > idleTimeout) {
                
                log.debug("清理空闲连接: {} (空闲时间: {}ms)", 
                         connection.getId(), now - connection.getLastUsedTime());
                removeConnection(connection);
            }
        });
    }
    
    /**
     * 生成连接ID
     */
    private String generateConnectionId() {
        return serverAddress.toString() + "-" + System.nanoTime();
    }
    
    /**
     * 获取连接池统计信息
     */
    public ConnectionPoolStats getStats() {
        return ConnectionPoolStats.builder()
                .serverAddress(serverAddress.toString())
                .totalConnections(totalConnections.get())
                .activeConnections(activeConnections.get())
                .availableConnections(availableConnections.size())
                .waitingRequests(waitingRequests.size())
                .connectionCreateCount(connectionCreateCount.get())
                .connectionCloseCount(connectionCloseCount.get())
                .requestWaitCount(requestWaitCount.get())
                .build();
    }
    
    /**
     * 关闭连接池
     */
    public void close() {
        if (closed) return;
        
        closed = true;
        
        // 关闭后台任务
        scheduler.shutdown();
        
        // 取消所有等待请求
        CompletableFuture<PooledConnection> waitingRequest;
        while ((waitingRequest = waitingRequests.poll()) != null) {
            waitingRequest.completeExceptionally(new IllegalStateException("连接池已关闭"));
        }
        
        // 关闭所有连接
        allConnections.values().forEach(this::removeConnection);
        
        log.info("连接池已关闭: {}", serverAddress);
    }
    
    /**
     * 检查连接池是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
}
