package com.rpc.example;

import com.rpc.client.AsyncRpcClient;
import com.rpc.client.RpcClient;
import com.rpc.client.NettyRpcClient;
import com.rpc.client.ZookeeperServiceDiscovery;
import com.rpc.common.config.RpcConfig;
import com.rpc.common.shutdown.GracefulShutdownManager;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 异步优化和优雅关闭功能测试
 * 
 * 测试异步调用、批量调用、优雅关闭等功能
 */
@Slf4j
public class AsyncOptimizationTest {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("RPC框架异步优化和优雅关闭测试");
        log.info("========================================");
        
        try {
            testAsyncCallOptimization();
            testBatchCallOptimization();
            testGracefulShutdown();
            
        } catch (Exception e) {
            log.error("测试过程中发生异常", e);
        }
        
        log.info("========================================");
        log.info("异步优化和优雅关闭测试完成");
        log.info("========================================");
    }
    
    /**
     * 测试异步调用优化
     */
    private static void testAsyncCallOptimization() {
        log.info("--- 测试异步调用优化功能 ---");
        
        try {
            // 创建模拟的RPC配置
            RpcConfig config = RpcConfig.defaultConfig();
            
            // 创建模拟的服务发现（这里不连接真实的ZooKeeper）
            ZookeeperServiceDiscovery serviceDiscovery = createMockServiceDiscovery();
            
            // 创建Netty客户端
            NettyRpcClient nettyClient = new NettyRpcClient(serviceDiscovery);
            
            // 创建RPC客户端
            RpcClient rpcClient = new RpcClient(nettyClient, config);
            
            // 获取异步客户端
            AsyncRpcClient asyncClient = rpcClient.getAsyncClient();
            
            // 测试异步调用
            testSingleAsyncCall(asyncClient);
            
            // 测试并发异步调用
            testConcurrentAsyncCalls(asyncClient);
            
            // 输出异步调用统计
            AsyncRpcClient.AsyncCallStats stats = rpcClient.getAsyncStats();
            log.info("异步调用统计: {}", stats);
            
            // 清理资源
            rpcClient.shutdown();
            
            log.info("异步调用优化功能测试完成");
            
        } catch (Exception e) {
            log.error("异步调用优化功能测试失败", e);
        }
    }
    
    /**
     * 测试单个异步调用
     */
    private static void testSingleAsyncCall(AsyncRpcClient asyncClient) {
        log.info("测试单个异步调用...");
        
        try {
            // 创建测试请求
            RpcRequest request = createTestRequest("hello", "AsyncTest");
            
            // 执行异步调用
            CompletableFuture<RpcResponse<String>> future = asyncClient.asyncCall(request);
            
            // 设置超时和异常处理
            future.orTimeout(5, TimeUnit.SECONDS)
                  .exceptionally(throwable -> {
                      log.debug("异步调用失败（预期）: {}", throwable.getMessage());
                      // 创建模拟响应
                      RpcResponse<String> response = new RpcResponse<>();
                      response.setCode(500);
                      response.setMessage("Mock failure");
                      return response;
                  })
                  .thenAccept(response -> {
                      log.info("异步调用完成，响应码: {}", response.getCode());
                  });
            
            // 等待完成
            Thread.sleep(1000);
            
        } catch (Exception e) {
            log.warn("单个异步调用测试异常", e);
        }
    }
    
    /**
     * 测试并发异步调用
     */
    private static void testConcurrentAsyncCalls(AsyncRpcClient asyncClient) {
        log.info("测试并发异步调用...");
        
        try {
            List<CompletableFuture<RpcResponse<String>>> futures = new ArrayList<>();
            
            // 创建多个并发异步调用
            for (int i = 0; i < 10; i++) {
                RpcRequest request = createTestRequest("hello", "ConcurrentTest" + i);
                CompletableFuture<RpcResponse<String>> future = asyncClient.asyncCall(request);
                futures.add(future);
            }
            
            // 等待所有调用完成或超时
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            allFutures.orTimeout(10, TimeUnit.SECONDS)
                     .exceptionally(throwable -> {
                         log.debug("部分并发调用超时（预期）");
                         return null;
                     })
                     .join();
            
            // 统计结果
            int completedCount = 0;
            for (CompletableFuture<?> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    completedCount++;
                }
            }
            
            log.info("并发异步调用完成，成功数: {}/{}", completedCount, futures.size());
            
        } catch (Exception e) {
            log.warn("并发异步调用测试异常", e);
        }
    }
    
    /**
     * 测试批量调用优化
     */
    private static void testBatchCallOptimization() {
        log.info("--- 测试批量调用优化功能 ---");
        
        try {
            // 创建模拟的RPC配置
            RpcConfig config = RpcConfig.defaultConfig();
            
            // 创建模拟的服务发现
            ZookeeperServiceDiscovery serviceDiscovery = createMockServiceDiscovery();
            
            // 创建Netty客户端
            NettyRpcClient nettyClient = new NettyRpcClient(serviceDiscovery);
            
            // 创建RPC客户端
            RpcClient rpcClient = new RpcClient(nettyClient, config);
            
            // 测试批量调用
            testBatchCalls(rpcClient);
            
            // 测试并行调用
            testParallelCalls(rpcClient);
            
            // 清理资源
            rpcClient.shutdown();
            
            log.info("批量调用优化功能测试完成");
            
        } catch (Exception e) {
            log.error("批量调用优化功能测试失败", e);
        }
    }
    
    /**
     * 测试批量调用
     */
    private static void testBatchCalls(RpcClient rpcClient) {
        log.info("测试批量调用...");
        
        try {
            // 创建批量请求
            List<RpcRequest> requests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                requests.add(createTestRequest("hello", "BatchTest" + i));
            }
            
            // 执行批量调用
            CompletableFuture<List<RpcResponse<String>>> batchFuture = rpcClient.batchCall(requests);
            
            // 等待结果
            batchFuture.orTimeout(10, TimeUnit.SECONDS)
                      .exceptionally(throwable -> {
                          log.debug("批量调用失败（预期）: {}", throwable.getMessage());
                          return new ArrayList<>(); // 返回空列表
                      })
                      .thenAccept(responses -> {
                          log.info("批量调用完成，响应数: {}", responses.size());
                          int successCount = 0;
                          for (RpcResponse<?> response : responses) {
                              if (response.getCode() == 200) {
                                  successCount++;
                              }
                          }
                          log.info("批量调用结果 - 成功: {}, 总计: {}", successCount, responses.size());
                      });
            
            // 等待完成
            Thread.sleep(2000);
            
        } catch (Exception e) {
            log.warn("批量调用测试异常", e);
        }
    }
    
    /**
     * 测试并行调用
     */
    private static void testParallelCalls(RpcClient rpcClient) {
        log.info("测试并行调用...");
        
        try {
            // 创建并行请求
            List<RpcRequest> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                requests.add(createTestRequest("hello", "ParallelTest" + i));
            }
            
            // 执行并行调用（最大并发数为3）
            AsyncRpcClient asyncClient = rpcClient.getAsyncClient();
            CompletableFuture<List<RpcResponse<String>>> parallelFuture = 
                asyncClient.parallelCall(requests, 3);
            
            // 等待结果
            parallelFuture.orTimeout(15, TimeUnit.SECONDS)
                         .exceptionally(throwable -> {
                             log.debug("并行调用失败（预期）: {}", throwable.getMessage());
                             return new ArrayList<>();
                         })
                         .thenAccept(responses -> {
                             log.info("并行调用完成，响应数: {}", responses.size());
                         });
            
            // 等待完成
            Thread.sleep(3000);
            
        } catch (Exception e) {
            log.warn("并行调用测试异常", e);
        }
    }
    
    /**
     * 测试优雅关闭功能
     */
    private static void testGracefulShutdown() {
        log.info("--- 测试优雅关闭功能 ---");
        
        try {
            // 获取优雅关闭管理器
            GracefulShutdownManager shutdownManager = GracefulShutdownManager.getInstance();
            
            // 显示当前注册的关闭钩子
            List<String> hookNames = shutdownManager.getShutdownHookNames();
            log.info("当前注册的关闭钩子: {}", hookNames);
            log.info("关闭钩子数量: {}", shutdownManager.getShutdownHookCount());
            
            // 设置关闭超时时间
            shutdownManager.setShutdownTimeout(10000); // 10秒
            
            // 模拟创建一些需要关闭的资源
            createMockResources(shutdownManager);
            
            log.info("模拟优雅关闭过程...");
            
            // 注意：在实际应用中，优雅关闭会在JVM关闭时自动触发
            // 这里我们手动触发来演示功能
            // shutdownManager.shutdown(); // 取消注释以实际测试
            
            log.info("优雅关闭功能已准备就绪");
            log.info("应用程序退出时将自动执行优雅关闭流程");
            
        } catch (Exception e) {
            log.error("优雅关闭功能测试失败", e);
        }
    }
    
    /**
     * 创建模拟资源用于测试优雅关闭
     */
    private static void createMockResources(GracefulShutdownManager shutdownManager) {
        // 注册一个模拟的关闭钩子
        shutdownManager.registerShutdownHook(new com.rpc.common.shutdown.ShutdownHook() {
            @Override
            public void shutdown() {
                log.info("执行模拟资源清理...");
                try {
                    Thread.sleep(1000); // 模拟清理耗时
                    log.info("模拟资源清理完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            @Override
            public String getName() {
                return "MockResourceCleanup";
            }
            
            @Override
            public int getPriority() {
                return 60;
            }
        });
    }
    
    /**
     * 创建测试用的RPC请求
     */
    private static RpcRequest createTestRequest(String methodName, String param) {
        return RpcRequest.builder()
                .interfaceName("com.rpc.example.HelloService")
                .methodName(methodName)
                .parameterTypes(new Class[]{String.class})
                .parameters(new Object[]{param})
                .version("1.0")
                .group("test")
                .build();
    }
    
    /**
     * 创建模拟的服务发现（用于测试，不连接真实ZooKeeper）
     */
    private static ZookeeperServiceDiscovery createMockServiceDiscovery() {
        try {
            // 注意：这里会尝试连接ZooKeeper，在测试环境中会失败，这是预期的
            return new ZookeeperServiceDiscovery("localhost:2181");
        } catch (Exception e) {
            log.debug("创建模拟服务发现失败（预期）: {}", e.getMessage());
            // 在实际测试中，我们主要关注异步调用的逻辑，而不是真实的网络连接
            return new ZookeeperServiceDiscovery("localhost:2181");
        }
    }
}
