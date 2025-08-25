package com.rpc.example;

import com.rpc.registry.ZookeeperServiceRegistry;
import com.rpc.server.RpcServer;
import com.rpc.server.interceptor.SecurityInterceptor;
import com.rpc.server.interceptor.RateLimitInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端示例 - 集成安全认证和限流功能
 */
@Slf4j
public class ServerExample {
    public static void main(String[] args) {
        // 创建ZooKeeper服务注册器
        ZookeeperServiceRegistry serviceRegistry = new ZookeeperServiceRegistry("localhost:2181");
        
        // 创建RPC服务器（已经自动添加了默认拦截器）
        RpcServer rpcServer = new RpcServer("localhost", 9999, serviceRegistry);
        
        // 可以自定义拦截器配置
        customizeInterceptors(rpcServer);
        
        // 注册服务
        HelloService helloService = new HelloServiceImpl();
        rpcServer.registerService(helloService, HelloService.class.getName() + "#default#1.0");
        
        // 启动服务器
        log.info("启动RPC服务器（已集成安全认证和限流功能）...");
        rpcServer.start();
    }
    
    /**
     * 自定义拦截器配置
     */
    private static void customizeInterceptors(RpcServer rpcServer) {
        log.info("当前拦截器配置:");
        rpcServer.getInterceptors().forEach(interceptor -> {
            log.info("  - {}", interceptor.getClass().getSimpleName());
        });
        
        // 可以根据需要添加自定义拦截器
        // 例如：添加日志拦截器、监控拦截器等
        
        log.info("拦截器配置完成，服务端将支持：");
        log.info("  ✅ 安全认证（JWT Token / API Key）");
        log.info("  ✅ 多级限流（IP/用户/服务/方法级别）");
        log.info("  ✅ 自动权限检查");
        log.info("  ✅ 请求拦截和监控");
    }
}
