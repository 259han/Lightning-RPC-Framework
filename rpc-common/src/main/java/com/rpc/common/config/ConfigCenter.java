package com.rpc.common.config;

import java.util.Map;

/**
 * 配置中心接口
 */
public interface ConfigCenter {
    
    /**
     * 获取配置值
     * @param key 配置键
     * @return 配置值
     */
    String getConfig(String key);
    
    /**
     * 获取配置值，如果不存在返回默认值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getConfig(String key, String defaultValue);
    
    /**
     * 设置配置值
     * @param key 配置键
     * @param value 配置值
     */
    void setConfig(String key, String value);
    
    /**
     * 删除配置
     * @param key 配置键
     */
    void removeConfig(String key);
    
    /**
     * 获取所有配置
     * @return 配置映射
     */
    Map<String, String> getAllConfig();
    
    /**
     * 添加配置变更监听器
     * @param key 配置键
     * @param listener 监听器
     */
    void addConfigListener(String key, ConfigChangeListener listener);
    
    /**
     * 移除配置变更监听器
     * @param key 配置键
     * @param listener 监听器
     */
    void removeConfigListener(String key, ConfigChangeListener listener);
    
    /**
     * 配置变更监听器
     */
    interface ConfigChangeListener {
        /**
         * 配置变更回调
         * @param key 配置键
         * @param oldValue 旧值
         * @param newValue 新值
         */
        void onConfigChange(String key, String oldValue, String newValue);
    }
}
