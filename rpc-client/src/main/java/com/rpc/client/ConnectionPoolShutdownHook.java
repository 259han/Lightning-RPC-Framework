package com.rpc.client;

import com.rpc.client.pool.ConnectionPoolManager;
import com.rpc.common.shutdown.ShutdownHook;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接池关闭钩子
 * 
 * 负责在应用关闭时正确清理连接池资源
 */
@Slf4j
public class ConnectionPoolShutdownHook implements ShutdownHook {
    
    private final ConnectionPoolManager poolManager;
    private final String poolName;
    
    public ConnectionPoolShutdownHook(ConnectionPoolManager poolManager, String poolName) {
        this.poolManager = poolManager;
        this.poolName = poolName != null ? poolName : "Default-ConnectionPool";
    }
    
    @Override
    public void shutdown() {
        try {
            log.info("开始关闭连接池: {}", poolName);
            
            if (poolManager != null && !poolManager.isClosed()) {
                // 输出最终统计
                log.info("连接池 {} 最终统计: {}", poolName, poolManager.getOverallStats());
                
                // 关闭连接池
                poolManager.close();
                log.info("连接池 {} 已成功关闭", poolName);
            }
            
        } catch (Exception e) {
            log.error("关闭连接池 {} 时发生错误", poolName, e);
        }
    }
    
    @Override
    public String getName() {
        return "ConnectionPool-" + poolName;
    }
    
    @Override
    public int getPriority() {
        return 30; // 高优先级，先关闭连接池
    }
    
    @Override
    public long getTimeoutMs() {
        return 15000; // 15秒超时
    }
}
