package com.rpc.registry;

import com.rpc.common.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于ZooKeeper的服务注册实现
 */
@Slf4j
public class ZookeeperServiceRegistry implements ServiceRegistry {
    private static final String ZK_REGISTER_ROOT_PATH = "/rpc-services";
    private final CuratorFramework zkClient;
    private final ConcurrentMap<String, String> registeredPaths = new ConcurrentHashMap<>();
    
    public ZookeeperServiceRegistry(String zkAddress) {
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
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            String servicePath = ZK_REGISTER_ROOT_PATH + "/" + serviceName;
            
            // 创建持久节点：/rpc-services/{serviceName}
            if (zkClient.checkExists().forPath(servicePath) == null) {
                zkClient.create()
                        .creatingParentsIfNeeded()
                        .forPath(servicePath);
                log.info("创建服务节点: {}", servicePath);
            }
            
            // 创建临时顺序节点：/rpc-services/{serviceName}/{serverAddress}-{sequence}
            String address = inetSocketAddress.getHostString() + ":" + inetSocketAddress.getPort();
            String instancePath = servicePath + "/" + address + "-";
            String actualPath = zkClient.create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(instancePath, address.getBytes());
            
            // 保存注册路径，用于注销时使用
            String key = serviceName + "#" + address;
            registeredPaths.put(key, actualPath);
            
            log.info("服务注册成功: {} -> {}", serviceName, actualPath);
        } catch (Exception e) {
            log.error("服务注册失败: {}", serviceName, e);
            throw new RuntimeException("服务注册失败", e);
        }
    }
    
    @Override
    public void unregisterService(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            String address = inetSocketAddress.getHostString() + ":" + inetSocketAddress.getPort();
            String key = serviceName + "#" + address;
            String registeredPath = registeredPaths.remove(key);
            
            if (registeredPath != null && zkClient.checkExists().forPath(registeredPath) != null) {
                zkClient.delete().forPath(registeredPath);
                log.info("服务注销成功: {} -> {}", serviceName, registeredPath);
            }
        } catch (Exception e) {
            log.error("服务注销失败: {}", serviceName, e);
        }
    }
    
    /**
     * 关闭ZooKeeper客户端
     */
    public void close() {
        if (zkClient != null) {
            zkClient.close();
            log.info("ZooKeeper客户端已关闭");
        }
    }
}
