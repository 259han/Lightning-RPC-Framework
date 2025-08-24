package com.rpc.common.registry;

import com.rpc.protocol.RpcRequest;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 服务发现接口
 */
public interface ServiceDiscovery {
    /**
     * 查找服务
     *
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    List<InetSocketAddress> lookupService(String serviceName);
    
    /**
     * 根据请求选择服务
     *
     * @param request 请求对象
     * @return 服务地址
     */
    InetSocketAddress selectServiceAddress(RpcRequest request);
}
