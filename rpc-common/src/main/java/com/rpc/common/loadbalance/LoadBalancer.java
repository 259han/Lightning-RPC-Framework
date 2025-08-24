package com.rpc.common.loadbalance;

import com.rpc.protocol.RpcRequest;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 负载均衡接口
 */
public interface LoadBalancer {
    /**
     * 从服务地址列表中选择一个
     *
     * @param serviceAddresses 服务地址列表
     * @param rpcRequest       RPC请求
     * @return 选择的服务地址
     */
    InetSocketAddress select(List<InetSocketAddress> serviceAddresses, RpcRequest rpcRequest);
}
