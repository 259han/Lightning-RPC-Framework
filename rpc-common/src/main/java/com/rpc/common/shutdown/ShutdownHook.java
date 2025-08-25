package com.rpc.common.shutdown;

/**
 * 关闭钩子接口
 * 
 * 定义组件在应用程序关闭时需要执行的清理操作
 */
public interface ShutdownHook {
    
    /**
     * 执行关闭操作
     * 
     * 该方法应该是幂等的，可以被多次调用而不产生副作用
     */
    void shutdown();
    
    /**
     * 获取钩子名称
     * 
     * @return 钩子的唯一标识名称
     */
    String getName();
    
    /**
     * 获取执行优先级
     * 
     * 数字越小优先级越高，优先级高的钩子会先执行
     * 
     * @return 优先级数值
     */
    default int getPriority() {
        return 100; // 默认优先级
    }
    
    /**
     * 获取钩子的超时时间（毫秒）
     * 
     * @return 超时时间，0表示使用全局超时设置
     */
    default long getTimeoutMs() {
        return 0; // 使用全局超时
    }
    
    /**
     * 检查钩子是否需要执行
     * 
     * @return true表示需要执行，false表示跳过
     */
    default boolean shouldExecute() {
        return true;
    }
}
