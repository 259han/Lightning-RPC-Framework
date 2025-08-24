package com.rpc.client;

import com.rpc.common.loadbalance.LoadBalancer;
import com.rpc.common.registry.ServiceDiscovery;
import com.rpc.extension.ExtensionLoader;
import com.rpc.protocol.RpcRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于ZooKeeper的服务发现实现
 */
@Slf4j
public class ZookeeperServiceDiscovery implements ServiceDiscovery {
    private static final String ZK_REGISTER_ROOT_PATH = "/rpc-services";
    private final CuratorFramework zkClient;
    private final ConcurrentMap<String, List<String>> serviceAddressCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PathChildrenCache> pathChildrenCacheMap = new ConcurrentHashMap<>();
    
    public ZookeeperServiceDiscovery(String zkAddress) {
        // 重试策略：初始睡眠时间为1s，最大重试次数为3次
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        
        this.zkClient = CuratorFrameworkFactory.builder()
                .connectString(zkAddress)
                .retryPolicy(retryPolicy)
                .build();
        
        this.zkClient.start();
        
        try {
            // 等待连接成功
            if (!zkClient.blockUntilConnected(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new RuntimeException("连接ZooKeeper超时");
            }
            log.info("成功连接到ZooKeeper: {}", zkAddress);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接ZooKeeper被中断", e);
        }
    }
    
    @Override
    public List<InetSocketAddress> lookupService(String serviceName) {
        if (serviceAddressCache.containsKey(serviceName)) {
            List<String> addresses = serviceAddressCache.get(serviceName);
            return convertToInetSocketAddresses(addresses);
        }
        
        String servicePath = ZK_REGISTER_ROOT_PATH + "/" + serviceName;
        
        try {
            if (zkClient.checkExists().forPath(servicePath) == null) {
                log.warn("服务路径不存在: {}", servicePath);
                return new ArrayList<>();
            }
            
            List<String> childrenNodes = zkClient.getChildren().forPath(servicePath);
            List<String> addresses = new ArrayList<>();
            
            for (String node : childrenNodes) {
                String nodePath = servicePath + "/" + node;
                byte[] data = zkClient.getData().forPath(nodePath);
                if (data != null) {
                    addresses.add(new String(data));
                }
            }
            
            // 缓存服务地址
            serviceAddressCache.put(serviceName, addresses);
            
            // 监听服务变化
            watchService(serviceName, servicePath);
            
            log.info("发现服务 [{}] 地址: {}", serviceName, addresses);
            return convertToInetSocketAddresses(addresses);
        } catch (Exception e) {
            log.error("查找服务失败: {}", serviceName, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public InetSocketAddress selectServiceAddress(RpcRequest request) {
        String serviceName = request.getRpcServiceName();
        List<InetSocketAddress> serviceAddresses = lookupService(serviceName);
        
        if (serviceAddresses.isEmpty()) {
            throw new RuntimeException("找不到可用的服务地址: " + serviceName);
        }
        
        // 使用负载均衡策略选择服务地址
        LoadBalancer loadBalancer = ExtensionLoader.getExtensionLoader(LoadBalancer.class)
                .getDefaultExtension();
        
        if (loadBalancer == null) {
            // 如果没有找到负载均衡器，使用简单的随机选择
            return serviceAddresses.get((int) (Math.random() * serviceAddresses.size()));
        }
        
        return loadBalancer.select(serviceAddresses, request);
    }
    
    /**
     * 监听服务变化
     */
    private void watchService(String serviceName, String servicePath) {
        if (pathChildrenCacheMap.containsKey(serviceName)) {
            return;
        }
        
        try {
            PathChildrenCache pathChildrenCache = new PathChildrenCache(zkClient, servicePath, true);
            
            pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                    log.info("服务 [{}] 发生变化，事件类型: {}", serviceName, event.getType());
                    
                    // 重新获取服务地址列表
                    List<String> childrenNodes = client.getChildren().forPath(servicePath);
                    List<String> addresses = new ArrayList<>();
                    
                    for (String node : childrenNodes) {
                        String nodePath = servicePath + "/" + node;
                        byte[] data = client.getData().forPath(nodePath);
                        if (data != null) {
                            addresses.add(new String(data));
                        }
                    }
                    
                    // 更新缓存
                    serviceAddressCache.put(serviceName, addresses);
                    log.info("更新服务 [{}] 地址缓存: {}", serviceName, addresses);
                }
            });
            
            pathChildrenCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
            pathChildrenCacheMap.put(serviceName, pathChildrenCache);
            
            log.info("开始监听服务变化: {}", servicePath);
        } catch (Exception e) {
            log.error("监听服务变化失败: {}", serviceName, e);
        }
    }
    
    /**
     * 转换为InetSocketAddress列表
     */
    private List<InetSocketAddress> convertToInetSocketAddresses(List<String> addresses) {
        List<InetSocketAddress> result = new ArrayList<>();
        for (String address : addresses) {
            String[] parts = address.split(":");
            if (parts.length == 2) {
                try {
                    String host = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    result.add(new InetSocketAddress(host, port));
                } catch (NumberFormatException e) {
                    log.warn("解析地址失败: {}", address);
                }
            }
        }
        return result;
    }
    
    /**
     * 关闭ZooKeeper客户端
     */
    public void close() {
        try {
            for (PathChildrenCache cache : pathChildrenCacheMap.values()) {
                cache.close();
            }
            pathChildrenCacheMap.clear();
            
            if (zkClient != null) {
                zkClient.close();
            }
            log.info("ZooKeeper客户端已关闭");
        } catch (Exception e) {
            log.error("关闭ZooKeeper客户端失败", e);
        }
    }
}
