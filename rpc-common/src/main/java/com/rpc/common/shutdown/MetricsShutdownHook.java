package com.rpc.common.shutdown;

import com.rpc.common.metrics.MetricsManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 指标管理器关闭钩子
 * 
 * 负责在应用关闭时正确清理指标收集资源
 */
@Slf4j
public class MetricsShutdownHook implements ShutdownHook {
    
    private final MetricsManager metricsManager;
    
    public MetricsShutdownHook(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }
    
    @Override
    public void shutdown() {
        try {
            log.info("开始关闭指标管理器");
            
            if (metricsManager != null) {
                // 生成最终报告
                log.info("生成最终性能报告:");
                metricsManager.generateManualReport();
                
                // 关闭指标管理器
                metricsManager.shutdown();
                log.info("指标管理器已成功关闭");
            }
            
        } catch (Exception e) {
            log.error("关闭指标管理器时发生错误", e);
        }
    }
    
    @Override
    public String getName() {
        return "MetricsManager";
    }
    
    @Override
    public int getPriority() {
        return 80; // 低优先级，最后关闭以收集其他组件的关闭指标
    }
    
    @Override
    public long getTimeoutMs() {
        return 5000; // 5秒超时
    }
}
