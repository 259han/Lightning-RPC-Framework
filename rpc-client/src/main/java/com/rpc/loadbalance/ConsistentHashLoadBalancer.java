package com.rpc.loadbalance;

import com.rpc.common.loadbalance.LoadBalancer;
import com.rpc.protocol.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希负载均衡策略
 * 
 * 特点：
 * - 使用虚拟节点解决数据倾斜问题
 * - 节点增减时只影响相邻节点的数据
 * - 适合有状态服务或需要会话保持的场景
 */
@Slf4j
public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    /**
     * 虚拟节点数量，用于解决数据倾斜问题
     */
    private static final int VIRTUAL_NODE_COUNT = 160;
    
    /**
     * 一致性哈希环缓存 <服务列表标识, 哈希环>
     */
    private final Map<String, TreeMap<Long, String>> hashRingCache = new ConcurrentHashMap<>();
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        
        // 如果只有一个地址，直接返回
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }
        
        // 生成服务列表的标识
        String serverListKey = generateServerListKey(serviceAddresses);
        
        // 获取或创建哈希环
        TreeMap<Long, String> hashRing = hashRingCache.computeIfAbsent(serverListKey, 
                key -> buildHashRing(serviceAddresses));
        
        // 根据请求特征计算哈希值
        String requestKey = buildRequestKey(rpcRequest);
        long hash = hash(requestKey);
        
        // 在哈希环中查找节点
        String selectedServer = selectServerFromRing(hashRing, hash);
        
        // 将字符串地址转换为InetSocketAddress
        return parseAddress(selectedServer);
    }
    
    /**
     * 构建哈希环
     */
    private TreeMap<Long, String> buildHashRing(List<InetSocketAddress> serviceAddresses) {
        TreeMap<Long, String> hashRing = new TreeMap<>();
        
        for (InetSocketAddress address : serviceAddresses) {
            String server = address.getHostString() + ":" + address.getPort();
            
            // 为每个真实节点创建多个虚拟节点
            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                String virtualNode = server + "#VN" + i;
                long hash = hash(virtualNode);
                hashRing.put(hash, server);
            }
        }
        
        log.debug("构建一致性哈希环，真实节点数: {}, 虚拟节点数: {}", 
                serviceAddresses.size(), hashRing.size());
        
        return hashRing;
    }
    
    /**
     * 从哈希环中选择服务器
     */
    private String selectServerFromRing(TreeMap<Long, String> hashRing, long hash) {
        // 查找第一个大于等于hash值的节点
        Map.Entry<Long, String> entry = hashRing.ceilingEntry(hash);
        
        // 如果没找到，说明hash值比所有节点都大，选择第一个节点（环形结构）
        if (entry == null) {
            entry = hashRing.firstEntry();
        }
        
        return entry.getValue();
    }
    
    /**
     * 构建请求特征键，用于哈希计算
     */
    private String buildRequestKey(RpcRequest rpcRequest) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 使用接口名+方法名作为基础键
        keyBuilder.append("service:").append(rpcRequest.getInterfaceName())
                 .append("#").append(rpcRequest.getMethodName());
        
        // 添加版本和分组信息以确保隔离
        if (rpcRequest.getVersion() != null) {
            keyBuilder.append("#version:").append(rpcRequest.getVersion());
        }
        if (rpcRequest.getGroup() != null) {
            keyBuilder.append("#group:").append(rpcRequest.getGroup());
        }
        
        // 如果有参数，添加第一个参数的哈希值（通常是用户ID等关键参数）
        if (rpcRequest.getParameters() != null && rpcRequest.getParameters().length > 0) {
            Object firstParam = rpcRequest.getParameters()[0];
            if (firstParam != null) {
                keyBuilder.append("#param:").append(firstParam.hashCode());
            }
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 生成服务列表的唯一标识
     */
    private String generateServerListKey(List<InetSocketAddress> serviceAddresses) {
        StringBuilder keyBuilder = new StringBuilder();
        serviceAddresses.stream()
                .sorted((a, b) -> {
                    int hostCompare = a.getHostString().compareTo(b.getHostString());
                    return hostCompare != 0 ? hostCompare : Integer.compare(a.getPort(), b.getPort());
                })
                .forEach(addr -> keyBuilder.append(addr.getHostString())
                        .append(":").append(addr.getPort()).append(","));
        return keyBuilder.toString();
    }
    
    /**
     * 解析地址字符串为InetSocketAddress
     */
    private InetSocketAddress parseAddress(String serverAddress) {
        String[] parts = serverAddress.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("无效的服务器地址格式: " + serverAddress);
        }
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }
    
    /**
     * 计算字符串的哈希值（使用MD5）
     */
    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // 取前8个字节转换为long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5算法不可用时，使用简单的哈希算法
            log.warn("MD5算法不可用，使用备用哈希算法");
            return key.hashCode();
        }
    }
    
    /**
     * 清理缓存（用于测试或服务列表大幅变动时）
     */
    public void clearCache() {
        hashRingCache.clear();
        log.info("一致性哈希环缓存已清理");
    }
    
    /**
     * 获取缓存统计信息
     */
    public int getCacheSize() {
        return hashRingCache.size();
    }
}
