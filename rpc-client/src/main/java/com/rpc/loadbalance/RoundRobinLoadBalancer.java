package com.rpc.loadbalance;

import com.rpc.common.loadbalance.LoadBalancer;
import com.rpc.protocol.RpcRequest;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡策略
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }
        
        // 使用原子操作获取下一个索引
        int index = Math.abs(currentIndex.getAndIncrement() % serviceAddresses.size());
        return serviceAddresses.get(index);
    }
}
