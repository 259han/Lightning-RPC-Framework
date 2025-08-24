package com.rpc.common.config;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于文件的配置中心实现
 */
@Slf4j
public class FileConfigCenter implements ConfigCenter {
    
    private final String configFilePath;
    private final Map<String, String> configMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigChangeListener>> listeners = new ConcurrentHashMap<>();
    private volatile boolean watching = false;
    private WatchService watchService;
    private Thread watchThread;
    
    public FileConfigCenter(String configFilePath) {
        this.configFilePath = configFilePath;
        loadConfig();
        startFileWatcher();
    }
    
    /**
     * 默认构造函数，使用默认配置文件路径
     */
    public FileConfigCenter() {
        this("rpc-config.properties");
    }
    
    @Override
    public String getConfig(String key) {
        return configMap.get(key);
    }
    
    @Override
    public String getConfig(String key, String defaultValue) {
        return configMap.getOrDefault(key, defaultValue);
    }
    
    @Override
    public void setConfig(String key, String value) {
        String oldValue = configMap.put(key, value);
        saveConfig();
        notifyListeners(key, oldValue, value);
    }
    
    @Override
    public void removeConfig(String key) {
        String oldValue = configMap.remove(key);
        if (oldValue != null) {
            saveConfig();
            notifyListeners(key, oldValue, null);
        }
    }
    
    @Override
    public Map<String, String> getAllConfig() {
        return new HashMap<>(configMap);
    }
    
    @Override
    public void addConfigListener(String key, ConfigChangeListener listener) {
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
    
    @Override
    public void removeConfigListener(String key, ConfigChangeListener listener) {
        List<ConfigChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            keyListeners.remove(listener);
            if (keyListeners.isEmpty()) {
                listeners.remove(key);
            }
        }
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            log.info("配置文件 {} 不存在，使用默认配置", configFilePath);
            createDefaultConfig();
            return;
        }
        
        try (InputStream input = new FileInputStream(configFile)) {
            Properties properties = new Properties();
            properties.load(input);
            
            configMap.clear();
            for (String key : properties.stringPropertyNames()) {
                configMap.put(key, properties.getProperty(key));
            }
            
            log.info("成功加载配置文件: {}, 共{}个配置项", configFilePath, configMap.size());
            
        } catch (IOException e) {
            log.error("加载配置文件失败: {}", configFilePath, e);
            createDefaultConfig();
        }
    }
    
    /**
     * 保存配置到文件
     */
    private void saveConfig() {
        try (OutputStream output = new FileOutputStream(configFilePath)) {
            Properties properties = new Properties();
            configMap.forEach(properties::setProperty);
            properties.store(output, "RPC Configuration - Generated at " + new Date());
            
            log.debug("配置已保存到文件: {}", configFilePath);
            
        } catch (IOException e) {
            log.error("保存配置文件失败: {}", configFilePath, e);
        }
    }
    
    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        log.info("创建默认配置");
        
        // 默认配置项
        configMap.put("rpc.request.timeout", "5000");
        configMap.put("rpc.connect.timeout", "3000");
        configMap.put("rpc.retry.enabled", "true");
        configMap.put("rpc.retry.max.attempts", "3");
        configMap.put("rpc.circuitbreaker.enabled", "true");
        configMap.put("rpc.circuitbreaker.failure.threshold", "5");
        configMap.put("rpc.circuitbreaker.recovery.timeout", "60000");
        configMap.put("rpc.serializer.type", "json");
        configMap.put("rpc.compressor.type", "gzip");
        configMap.put("rpc.loadbalancer.type", "random");
        configMap.put("rpc.zookeeper.address", "localhost:2181");
        configMap.put("rpc.zookeeper.session.timeout", "30000");
        configMap.put("rpc.zookeeper.connection.timeout", "10000");
        
        saveConfig();
    }
    
    /**
     * 启动文件监控
     */
    private void startFileWatcher() {
        try {
            Path configPath = Paths.get(configFilePath);
            Path parentDir = configPath.getParent();
            if (parentDir == null) {
                parentDir = Paths.get(".");
            }
            
            watchService = FileSystems.getDefault().newWatchService();
            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            
            watching = true;
            watchThread = new Thread(this::watchConfigFile, "ConfigFileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            
            log.info("启动配置文件监控: {}", configFilePath);
            
        } catch (IOException e) {
            log.error("启动配置文件监控失败", e);
        }
    }
    
    /**
     * 监控配置文件变化
     */
    private void watchConfigFile() {
        String fileName = Paths.get(configFilePath).getFileName().toString();
        
        while (watching) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changedFile = (Path) event.context();
                        if (fileName.equals(changedFile.toString())) {
                            log.info("检测到配置文件变更，重新加载: {}", configFilePath);
                            // 延迟一点时间，确保文件写入完成
                            Thread.sleep(100);
                            reloadConfig();
                        }
                    }
                }
                
                key.reset();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("配置文件监控异常", e);
            }
        }
    }
    
    /**
     * 重新加载配置
     */
    private void reloadConfig() {
        Map<String, String> oldConfig = new HashMap<>(configMap);
        loadConfig();
        
        // 通知配置变更
        Set<String> allKeys = new HashSet<>(oldConfig.keySet());
        allKeys.addAll(configMap.keySet());
        
        for (String key : allKeys) {
            String oldValue = oldConfig.get(key);
            String newValue = configMap.get(key);
            
            if (!Objects.equals(oldValue, newValue)) {
                notifyListeners(key, oldValue, newValue);
            }
        }
    }
    
    /**
     * 通知配置变更监听器
     */
    private void notifyListeners(String key, String oldValue, String newValue) {
        List<ConfigChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            for (ConfigChangeListener listener : keyListeners) {
                try {
                    listener.onConfigChange(key, oldValue, newValue);
                } catch (Exception e) {
                    log.error("配置变更监听器执行异常: key={}", key, e);
                }
            }
        }
    }
    
    /**
     * 关闭配置中心
     */
    public void shutdown() {
        watching = false;
        
        if (watchThread != null) {
            watchThread.interrupt();
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("关闭文件监控服务异常", e);
            }
        }
        
        log.info("配置中心已关闭");
    }
}
