package com.rpc.client.pool;

import com.rpc.common.config.ConnectionPoolConfig;
import io.netty.bootstrap.Bootstrap;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 连接池管理器
 * 
 * 管理多个服务器的连接池，提供统一的连接获取和统计接口
 */
@Slf4j
public class ConnectionPoolManager {
    
    private final ConnectionPoolConfig config;
    private final Bootstrap bootstrap;
    private final ConcurrentMap<String, ConnectionPool> connectionPools;
    private final ScheduledExecutorService statsScheduler;
    
    private volatile boolean closed = false;
    
    public ConnectionPoolManager(ConnectionPoolConfig config, Bootstrap bootstrap) {
        this.config = config;
        this.bootstrap = bootstrap;
        this.connectionPools = new ConcurrentHashMap<>();
        
        // 统计信息定期输出
        this.statsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionPoolManager-Stats");
            t.setDaemon(true);
            return t;
        });
        
        // 每30秒输出一次统计信息
        startStatsReporting();
        
        log.info("连接池管理器已初始化");
    }
    
    /**
     * 获取指定服务器的连接
     */
    public CompletableFuture<PooledConnection> getConnection(InetSocketAddress serverAddress) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("连接池管理器已关闭"));
        }
        
        if (!config.isEnabled()) {
            // 连接池未启用，直接创建连接
            return createDirectConnection(serverAddress);
        }
        
        String serverKey = serverAddress.toString();
        ConnectionPool pool = connectionPools.computeIfAbsent(serverKey, 
            key -> new ConnectionPool(serverAddress, config, bootstrap));
        
        return pool.getConnection();
    }
    
    /**
     * 创建直接连接（不使用连接池）
     */
    private CompletableFuture<PooledConnection> createDirectConnection(InetSocketAddress serverAddress) {
        CompletableFuture<PooledConnection> future = new CompletableFuture<>();
        
        bootstrap.connect(serverAddress).addListener(f -> {
            if (f.isSuccess()) {
                String connectionId = "direct-" + System.nanoTime();
                PooledConnection connection = new PooledConnection(
                    connectionId, 
                    ((io.netty.channel.ChannelFuture) f).channel(), 
                    null,  // 无连接池
                    System.currentTimeMillis()
                );
                connection.markInUse();
                future.complete(connection);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        
        return future;
    }
    
    /**
     * 获取所有连接池的统计信息
     */
    public List<ConnectionPoolStats> getAllStats() {
        return connectionPools.values().stream()
                .map(ConnectionPool::getStats)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取指定服务器的连接池统计信息
     */
    public ConnectionPoolStats getStats(InetSocketAddress serverAddress) {
        String serverKey = serverAddress.toString();
        ConnectionPool pool = connectionPools.get(serverKey);
        return pool != null ? pool.getStats() : null;
    }
    
    /**
     * 获取总体统计信息
     */
    public OverallConnectionStats getOverallStats() {
        List<ConnectionPoolStats> allStats = getAllStats();
        
        int totalPools = allStats.size();
        int totalConnections = allStats.stream().mapToInt(ConnectionPoolStats::getTotalConnections).sum();
        int totalActive = allStats.stream().mapToInt(ConnectionPoolStats::getActiveConnections).sum();
        int totalAvailable = allStats.stream().mapToInt(ConnectionPoolStats::getAvailableConnections).sum();
        int totalWaiting = allStats.stream().mapToInt(ConnectionPoolStats::getWaitingRequests).sum();
        long totalCreated = allStats.stream().mapToLong(ConnectionPoolStats::getConnectionCreateCount).sum();
        long totalClosed = allStats.stream().mapToLong(ConnectionPoolStats::getConnectionCloseCount).sum();
        
        return OverallConnectionStats.builder()
                .totalPools(totalPools)
                .totalConnections(totalConnections)
                .totalActiveConnections(totalActive)
                .totalAvailableConnections(totalAvailable)
                .totalWaitingRequests(totalWaiting)
                .totalConnectionsCreated(totalCreated)
                .totalConnectionsClosed(totalClosed)
                .build();
    }
    
    /**
     * 移除指定服务器的连接池
     */
    public void removeConnectionPool(InetSocketAddress serverAddress) {
        String serverKey = serverAddress.toString();
        ConnectionPool pool = connectionPools.remove(serverKey);
        if (pool != null) {
            pool.close();
            log.info("已移除连接池: {}", serverAddress);
        }
    }
    
    /**
     * 清理不健康的连接池
     */
    public void cleanupUnhealthyPools() {
        connectionPools.entrySet().removeIf(entry -> {
            ConnectionPool pool = entry.getValue();
            ConnectionPoolStats stats = pool.getStats();
            
            // 如果连接池长时间没有连接且没有等待请求，认为不健康
            boolean unhealthy = stats.getTotalConnections() == 0 && 
                              stats.getWaitingRequests() == 0 &&
                              pool.isClosed();
            
            if (unhealthy) {
                log.info("清理不健康的连接池: {}", entry.getKey());
                pool.close();
                return true;
            }
            return false;
        });
    }
    
    /**
     * 启动统计信息报告
     */
    private void startStatsReporting() {
        statsScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (closed || connectionPools.isEmpty()) {
                    return;
                }
                
                OverallConnectionStats overallStats = getOverallStats();
                log.info("连接池总体统计: {}", overallStats);
                
                // 输出每个池的详细统计（DEBUG级别）
                if (log.isDebugEnabled()) {
                    getAllStats().forEach(stats -> {
                        if (stats.getTotalConnections() > 0) {
                            log.debug("连接池详情: {}", stats);
                        }
                    });
                }
                
                // 清理不健康的连接池
                cleanupUnhealthyPools();
                
            } catch (Exception e) {
                log.warn("统计信息报告异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 关闭连接池管理器
     */
    public void close() {
        if (closed) return;
        
        closed = true;
        
        // 关闭统计调度器
        statsScheduler.shutdown();
        
        // 关闭所有连接池
        connectionPools.values().forEach(ConnectionPool::close);
        connectionPools.clear();
        
        log.info("连接池管理器已关闭");
    }
    
    /**
     * 检查是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 获取连接池数量
     */
    public int getPoolCount() {
        return connectionPools.size();
    }
}
