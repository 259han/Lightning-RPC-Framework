package com.rpc.example;

import com.rpc.client.NettyRpcClient;
import com.rpc.client.RpcClient;
import com.rpc.client.ZookeeperServiceDiscovery;
import com.rpc.common.security.AuthenticationManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端示例 - 集成安全认证功能
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
            
            // 配置认证信息
            setupAuthentication(rpcClient);
            
            // 获取服务代理
            HelloService helloService = rpcClient.getProxy(HelloService.class, "1.0", "default");
            
            // 调用远程方法
            log.info("开始调用远程服务（已配置认证信息）...");
            
            String result1 = helloService.hello("World");
            log.info("调用hello方法结果: {}", result1);
            
            int result2 = helloService.add(10, 20);
            log.info("调用add方法结果: {}", result2);
            
            log.info("所有调用完成，认证和限流功能正常工作！");
            
            // 关闭客户端
            nettyRpcClient.close();
            serviceDiscovery.close();
        } catch (Exception e) {
            log.error("客户端运行异常", e);
        }
    }
    
    /**
     * 设置认证信息
     */
    private static void setupAuthentication(RpcClient rpcClient) {
        try {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            
            // 生成JWT Token
            String userId = "client-example-user";
            String[] roles = {"user", "read", "write"};
            String jwtToken = authManager.generateJwtToken(userId, roles);
            
            // 设置全局认证Token
            rpcClient.setGlobalAuthToken(jwtToken);
            
            log.info("客户端认证配置完成:");
            log.info("  ✅ 用户ID: {}", userId);
            log.info("  ✅ 角色: {}", String.join(", ", roles));
            log.info("  ✅ JWT Token: {}...", jwtToken.substring(0, Math.min(20, jwtToken.length())));
            
        } catch (Exception e) {
            log.error("配置认证信息失败", e);
            throw new RuntimeException("认证配置失败", e);
        }
    }
}
