package com.rpc.example;

import com.rpc.client.NettyRpcClient;
import com.rpc.client.RpcClient;
import com.rpc.client.ZookeeperServiceDiscovery;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端示例
 */
@Slf4j
public class ClientExample {
    public static void main(String[] args) {
        try {
            // 创建ZooKeeper服务发现器
            ZookeeperServiceDiscovery serviceDiscovery = new ZookeeperServiceDiscovery("localhost:2181");
            
            // 创建Netty RPC客户端
            NettyRpcClient nettyRpcClient = new NettyRpcClient(serviceDiscovery);
            
            // 创建RPC客户端
            RpcClient rpcClient = new RpcClient(nettyRpcClient, 5000);
            
            // 获取服务代理
            HelloService helloService = rpcClient.getProxy(HelloService.class, "1.0", "default");
            
            // 调用远程方法
            String result1 = helloService.hello("World");
            log.info("调用hello方法结果: {}", result1);
            
            int result2 = helloService.add(10, 20);
            log.info("调用add方法结果: {}", result2);
            
            // 关闭客户端
            nettyRpcClient.close();
            serviceDiscovery.close();
        } catch (Exception e) {
            log.error("客户端运行异常", e);
        }
    }
}
