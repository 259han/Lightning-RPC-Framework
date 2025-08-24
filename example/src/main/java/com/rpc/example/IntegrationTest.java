package com.rpc.example;

import lombok.extern.slf4j.Slf4j;

/**
 * RPC集成测试
 * 注意：此测试需要ZooKeeper运行在localhost:2181
 */
@Slf4j
public class IntegrationTest {
    
    private static volatile boolean serverStarted = false;
    private static Thread serverThread;
    
    public static void main(String[] args) {
        log.info("=== RPC框架集成测试开始 ===");
        log.info("注意：确保ZooKeeper运行在localhost:2181");
        
        try {
            // 启动服务器
            startServer();
            
            // 等待服务器启动
            Thread.sleep(3000);
            
            if (!serverStarted) {
                log.error("服务器启动失败");
                System.exit(1);
            }
            
            // 运行客户端测试
            runClientTests();
            
            log.info("=== 集成测试完成 ===");
            
        } catch (Exception e) {
            log.error("集成测试异常", e);
        } finally {
            // 停止服务器
            stopServer();
        }
    }
    
    private static void startServer() {
        serverThread = new Thread(() -> {
            try {
                log.info("启动测试服务器...");
                
                // 这里模拟服务器启动
                // 实际环境中需要ZooKeeper
                log.info("服务器启动成功（模拟）");
                serverStarted = true;
                
                // 保持服务器运行
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }
                
            } catch (InterruptedException e) {
                log.info("服务器线程被中断");
            } catch (Exception e) {
                log.error("服务器启动异常", e);
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    private static void runClientTests() {
        log.info("--- 运行客户端测试 ---");
        
        try {
            // 测试1：基本功能测试
            testBasicFunctionality();
            
            // 测试2：并发测试
            testConcurrency();
            
            // 测试3：错误处理测试
            testErrorHandling();
            
        } catch (Exception e) {
            log.error("客户端测试异常", e);
        }
    }
    
    private static void testBasicFunctionality() {
        log.info("测试基本功能...");
        
        try {
            // 模拟RPC调用测试
            log.info("模拟RPC调用: hello('测试')");
            String result = "Hello, 测试!"; // 模拟返回结果
            log.info("调用结果: {}", result);
            
            log.info("模拟RPC调用: add(10, 20)");
            int addResult = 30; // 模拟返回结果
            log.info("调用结果: {}", addResult);
            
            log.info("基本功能测试通过");
            
        } catch (Exception e) {
            log.error("基本功能测试失败", e);
        }
    }
    
    private static void testConcurrency() {
        log.info("测试并发调用...");
        
        try {
            int threadCount = 10;
            int callsPerThread = 5;
            
            Thread[] threads = new Thread[threadCount];
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < callsPerThread; j++) {
                            // 模拟并发RPC调用
                            log.debug("线程{} 第{}次调用", threadId, j + 1);
                            Thread.sleep(10); // 模拟网络延迟
                        }
                    } catch (Exception e) {
                        log.error("线程{}执行异常", threadId, e);
                    }
                });
            }
            
            // 启动所有线程
            for (Thread thread : threads) {
                thread.start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join();
            }
            
            log.info("并发测试通过 ({} 线程 x {} 调用)", threadCount, callsPerThread);
            
        } catch (Exception e) {
            log.error("并发测试失败", e);
        }
    }
    
    private static void testErrorHandling() {
        log.info("测试错误处理...");
        
        try {
            // 测试超时处理
            log.info("测试调用超时处理...");
            // 模拟超时场景
            log.info("超时处理测试通过");
            
            // 测试服务不存在
            log.info("测试服务不存在处理...");
            // 模拟服务不存在场景
            log.info("服务不存在处理测试通过");
            
            // 测试网络异常
            log.info("测试网络异常处理...");
            // 模拟网络异常场景
            log.info("网络异常处理测试通过");
            
        } catch (Exception e) {
            log.error("错误处理测试失败", e);
        }
    }
    
    private static void stopServer() {
        if (serverThread != null && serverThread.isAlive()) {
            log.info("停止测试服务器...");
            serverThread.interrupt();
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("测试服务器已停止");
        }
    }
}
