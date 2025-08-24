package com.rpc.example;

import com.rpc.registry.ZookeeperServiceRegistry;
import com.rpc.server.RpcServer;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端示例
 */
@Slf4j
public class ServerExample {
    public static void main(String[] args) {
        // 创建ZooKeeper服务注册器
        ZookeeperServiceRegistry serviceRegistry = new ZookeeperServiceRegistry("localhost:2181");
        
        // 创建RPC服务器
        RpcServer rpcServer = new RpcServer("localhost", 9999, serviceRegistry);
        
        // 注册服务
        HelloService helloService = new HelloServiceImpl();
        rpcServer.registerService(helloService, HelloService.class.getName() + "#default#1.0");
        
        // 启动服务器
        log.info("启动RPC服务器...");
        rpcServer.start();
    }
}
