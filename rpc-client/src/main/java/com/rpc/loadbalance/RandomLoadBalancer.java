package com.rpc.loadbalance;

import com.rpc.common.loadbalance.LoadBalancer;
import com.rpc.protocol.RpcRequest;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡策略
 */
public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }
        
        // 随机选择一个地址
        return serviceAddresses.get(random.nextInt(serviceAddresses.size()));
    }
}
