package com.rpc.client;

import com.rpc.common.shutdown.ShutdownHook;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC客户端关闭钩子
 * 
 * 负责在应用关闭时正确清理RPC客户端资源
 */
@Slf4j
public class RpcClientShutdownHook implements ShutdownHook {
    
    private final RpcClient rpcClient;
    private final String clientName;
    
    public RpcClientShutdownHook(RpcClient rpcClient, String clientName) {
        this.rpcClient = rpcClient;
        this.clientName = clientName != null ? clientName : "Default-RpcClient";
    }
    
    @Override
    public void shutdown() {
        try {
            log.info("开始关闭RPC客户端: {}", clientName);
            
            if (rpcClient != null) {
                rpcClient.shutdown();
                log.info("RPC客户端 {} 已成功关闭", clientName);
            }
            
        } catch (Exception e) {
            log.error("关闭RPC客户端 {} 时发生错误", clientName, e);
        }
    }
    
    @Override
    public String getName() {
        return "RpcClient-" + clientName;
    }
    
    @Override
    public int getPriority() {
        return 50; // 中等优先级，在连接池之后，在指标管理器之前
    }
    
    @Override
    public long getTimeoutMs() {
        return 10000; // 10秒超时
    }
}
