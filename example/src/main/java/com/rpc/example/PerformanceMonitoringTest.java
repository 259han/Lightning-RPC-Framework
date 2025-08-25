package com.rpc.example;

import com.rpc.common.config.ConnectionPoolConfig;
import com.rpc.client.pool.ConnectionPoolStats;
import com.rpc.client.pool.OverallConnectionStats;
import com.rpc.client.pool.ConnectionPoolManager;
import com.rpc.common.metrics.MetricsManager;
import com.rpc.common.metrics.MetricsSnapshot;
import com.rpc.common.config.RpcConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控功能测试
 * 
 * 测试连接池管理和性能指标收集功能
 */
@Slf4j
public class PerformanceMonitoringTest {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("RPC框架性能监控功能测试");
        log.info("========================================");
        
        testConnectionPoolOptimization();
        testMetricsCollection();
        
        log.info("========================================");
        log.info("性能监控功能测试完成");
        log.info("========================================");
    }
    
    /**
     * 测试连接池优化功能
     */
    private static void testConnectionPoolOptimization() {
        log.info("--- 测试连接池优化功能 ---");
        
        try {
            // 创建连接池配置
            ConnectionPoolConfig poolConfig = ConnectionPoolConfig.builder()
                    .maxConnectionsPerServer(5)
                    .connectionIdleTimeout(30000)
                    .healthCheckInterval(10000)
                    .maxPendingRequests(20)
                    .warmupConnections(2)
                    .enabled(true)
                    .build();
            
            log.info("连接池配置: {}", poolConfig);
            
            // 创建Bootstrap（模拟Netty客户端）
            NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .option(ChannelOption.SO_KEEPALIVE, true);
            
            // 创建连接池管理器
            ConnectionPoolManager poolManager = new ConnectionPoolManager(poolConfig, bootstrap);
            
            // 测试连接池统计
            testConnectionPoolStats(poolManager);
            
            // 模拟并发连接获取
            testConcurrentConnections(poolManager);
            
            // 输出最终统计
            OverallConnectionStats finalStats = poolManager.getOverallStats();
            log.info("最终连接池统计: {}", finalStats);
            
            // 清理资源
            poolManager.close();
            eventLoopGroup.shutdownGracefully();
            
            log.info("连接池优化功能测试完成");
            
        } catch (Exception e) {
            log.error("连接池优化功能测试失败", e);
        }
    }
    
    /**
     * 测试连接池统计功能
     */
    private static void testConnectionPoolStats(ConnectionPoolManager poolManager) {
        log.info("--- 测试连接池统计功能 ---");
        
        try {
            // 获取初始统计
            OverallConnectionStats initialStats = poolManager.getOverallStats();
            log.info("初始连接池统计: {}", initialStats);
            
            // 模拟获取连接
            InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
            
            // 注意：实际测试中，因为没有真实的服务器，连接会失败
            // 这里主要测试统计功能的正确性
            CompletableFuture.runAsync(() -> {
                try {
                    poolManager.getConnection(testAddress)
                        .exceptionally(throwable -> {
                            log.debug("预期的连接失败: {}", throwable.getMessage());
                            return null;
                        });
                } catch (Exception e) {
                    log.debug("连接测试异常（预期）: {}", e.getMessage());
                }
            });
            
            // 等待一段时间让统计更新
            Thread.sleep(1000);
            
            // 获取更新后的统计
            OverallConnectionStats updatedStats = poolManager.getOverallStats();
            log.info("更新后连接池统计: {}", updatedStats);
            
        } catch (Exception e) {
            log.warn("连接池统计测试异常", e);
        }
    }
    
    /**
     * 测试并发连接获取
     */
    private static void testConcurrentConnections(ConnectionPoolManager poolManager) {
        log.info("--- 测试并发连接获取 ---");
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try {
            InetSocketAddress testAddress = new InetSocketAddress("127.0.0.1", 8080);
            
            // 提交多个并发任务
            for (int i = 0; i < 20; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        log.debug("任务{}开始获取连接", taskId);
                        poolManager.getConnection(testAddress)
                            .exceptionally(throwable -> {
                                log.debug("任务{}连接失败（预期）: {}", taskId, throwable.getMessage());
                                return null;
                            });
                    } catch (Exception e) {
                        log.debug("任务{}异常: {}", taskId, e.getMessage());
                    }
                });
            }
            
            // 等待任务完成
            Thread.sleep(2000);
            
            // 输出并发测试统计
            OverallConnectionStats concurrentStats = poolManager.getOverallStats();
            log.info("并发测试连接池统计: {}", concurrentStats);
            
        } catch (Exception e) {
            log.warn("并发连接测试异常", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
    
    /**
     * 测试性能指标收集功能
     */
    private static void testMetricsCollection() {
        log.info("--- 测试性能指标收集功能 ---");
        
        try {
            MetricsManager metricsManager = MetricsManager.getInstance();
            
            // 模拟一些RPC调用指标
            simulateRpcCalls(metricsManager);
            
            // 获取指标快照
            MetricsSnapshot helloServiceSnapshot = metricsManager.getSnapshot("com.rpc.example.HelloService");
            log.info("HelloService指标快照: {}", helloServiceSnapshot);
            
            MetricsSnapshot userServiceSnapshot = metricsManager.getSnapshot("com.rpc.example.UserService");
            log.info("UserService指标快照: {}", userServiceSnapshot);
            
            // 获取总体指标快照
            MetricsSnapshot allSnapshot = metricsManager.getAllSnapshot();
            log.info("总体指标快照: {}", allSnapshot);
            
            // 手动生成一次报告
            log.info("生成手动性能报告:");
            metricsManager.generateManualReport();
            
            // 关闭指标管理器以确保程序正常退出
            log.info("关闭指标管理器...");
            metricsManager.shutdown();
            
            log.info("性能指标收集功能测试完成");
            
        } catch (Exception e) {
            log.error("性能指标收集功能测试失败", e);
        }
    }
    
    /**
     * 模拟RPC调用以生成指标数据
     */
    private static void simulateRpcCalls(MetricsManager metricsManager) {
        log.info("模拟RPC调用生成指标数据...");
        
        // 模拟HelloService调用
        for (int i = 0; i < 50; i++) {
            // 模拟正常调用
            if (i % 10 != 0) {
                long responseTime = 50 + (long) (Math.random() * 200); // 50-250ms
                metricsManager.recordThroughput("com.rpc.example.HelloService", "hello");
                metricsManager.recordSuccess("com.rpc.example.HelloService", "hello");
                metricsManager.recordRequestTime("com.rpc.example.HelloService", "hello", responseTime);
            } else {
                // 模拟失败调用
                long responseTime = 100 + (long) (Math.random() * 1000); // 100-1100ms
                metricsManager.recordThroughput("com.rpc.example.HelloService", "hello");
                metricsManager.recordError("com.rpc.example.HelloService", "hello", 
                    new RuntimeException("模拟网络异常"));
                metricsManager.recordRequestTime("com.rpc.example.HelloService", "hello", responseTime);
            }
        }
        
        // 模拟UserService调用
        for (int i = 0; i < 30; i++) {
            // 模拟正常调用，响应时间较长
            if (i % 5 != 0) {
                long responseTime = 200 + (long) (Math.random() * 500); // 200-700ms
                metricsManager.recordThroughput("com.rpc.example.UserService", "getUserInfo");
                metricsManager.recordSuccess("com.rpc.example.UserService", "getUserInfo");
                metricsManager.recordRequestTime("com.rpc.example.UserService", "getUserInfo", responseTime);
            } else {
                // 模拟失败调用
                long responseTime = 500 + (long) (Math.random() * 2000); // 500-2500ms
                metricsManager.recordThroughput("com.rpc.example.UserService", "getUserInfo");
                metricsManager.recordError("com.rpc.example.UserService", "getUserInfo", 
                    new RuntimeException("模拟数据库超时"));
                metricsManager.recordRequestTime("com.rpc.example.UserService", "getUserInfo", responseTime);
            }
        }
        
        // 模拟连接池使用情况
        metricsManager.recordConnectionPool("127.0.0.1:8080", 8, 10, 2);
        metricsManager.recordConnectionPool("127.0.0.1:8081", 5, 10, 0);
        metricsManager.recordConnectionPool("127.0.0.1:8082", 3, 10, 1);
        
        log.info("指标数据模拟完成");
    }
}
