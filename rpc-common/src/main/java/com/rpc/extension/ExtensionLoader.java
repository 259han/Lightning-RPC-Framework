package com.rpc.extension;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPI扩展加载器
 */
@Slf4j
public class ExtensionLoader<T> {
    /**
     * 扩展点配置文件路径
     */
    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    
    /**
     * 扩展加载器缓存 <扩展点类型, 加载器实例>
     */
    private static final Map<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();
    
    /**
     * 扩展点实例缓存 <扩展点类型, 扩展点实例>
     */
    private static final Map<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();
    
    /**
     * 当前扩展点类型
     */
    private final Class<T> type;
    
    /**
     * 当前扩展点实现类缓存 <扩展名, 实现类>
     */
    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    
    /**
     * 当前扩展点实例缓存 <扩展名, 实例>
     */
    private final Map<String, T> cachedInstances = new ConcurrentHashMap<>();
    
    /**
     * 构造方法
     *
     * @param type 扩展点类型
     */
    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }
    
    /**
     * 获取扩展加载器
     *
     * @param type 扩展点类型
     * @param <T>  扩展点类型
     * @return 扩展加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("扩展点类型不能为空");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("扩展点类型必须是接口");
        }
        
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }
    
    /**
     * 获取默认扩展点实现
     *
     * @return 扩展点实现
     */
    public T getDefaultExtension() {
        loadExtensionClasses();
        if (cachedClasses.isEmpty()) {
            return null;
        }
        // 默认取第一个
        String name = cachedClasses.keySet().iterator().next();
        return getExtension(name);
    }
    
    /**
     * 获取指定名称的扩展点实现
     *
     * @param name 扩展点名称
     * @return 扩展点实现
     */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("扩展点名称不能为空");
        }
        
        T instance = cachedInstances.get(name);
        if (instance == null) {
            synchronized (cachedInstances) {
                instance = cachedInstances.get(name);
                if (instance == null) {
                    instance = createExtension(name);
                    cachedInstances.put(name, instance);
                }
            }
        }
        return instance;
    }
    
    /**
     * 创建扩展点实例
     *
     * @param name 扩展点名称
     * @return 扩展点实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new IllegalStateException("没有找到扩展点 [" + name + "] 的实现类");
        }
        
        try {
            Object instance = EXTENSION_INSTANCES.computeIfAbsent(clazz, aClass -> {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("实例化扩展点 [" + name + "] 失败", e);
                }
            });
            return (T) instance;
        } catch (Throwable t) {
            throw new IllegalStateException("实例化扩展点 [" + name + "] 失败", t);
        }
    }
    
    /**
     * 获取所有扩展点实现类
     *
     * @return 扩展点实现类映射
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses;
        if (classes.isEmpty()) {
            synchronized (cachedClasses) {
                classes = cachedClasses;
                if (classes.isEmpty()) {
                    loadExtensionClasses();
                    classes = cachedClasses;
                }
            }
        }
        return classes;
    }
    
    /**
     * 加载扩展点实现类
     */
    private void loadExtensionClasses() {
        String fileName = SERVICES_DIRECTORY + type.getName();
        try {
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            Enumeration<URL> urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    loadResource(url);
                }
            }
        } catch (IOException e) {
            log.error("加载扩展点配置文件失败 [{}]", fileName, e);
        }
    }
    
    /**
     * 加载资源
     *
     * @param url 资源URL
     */
    private void loadResource(URL url) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 去除注释
                final int ci = line.indexOf('#');
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        String name = null;
                        int i = line.indexOf('=');
                        if (i > 0) {
                            name = line.substring(0, i).trim();
                            line = line.substring(i + 1).trim();
                        }
                        if (line.length() > 0) {
                            loadClass(line, name);
                        }
                    } catch (Throwable t) {
                        log.error("加载扩展点实现类失败 [{}]", line, t);
                    }
                }
            }
        } catch (IOException e) {
            log.error("加载扩展点资源失败 [{}]", url, e);
        }
    }
    
    /**
     * 加载类
     *
     * @param className 类名
     * @param name      扩展点名称
     */
    private void loadClass(String className, String name) {
        try {
            Class<?> clazz = Class.forName(className, true, ExtensionLoader.class.getClassLoader());
            if (!type.isAssignableFrom(clazz)) {
                throw new IllegalStateException("扩展点实现类 [" + className + "] 不是 " + type.getName() + " 的子类");
            }
            
            cachedClasses.putIfAbsent(name, clazz);
        } catch (ClassNotFoundException e) {
            log.error("扩展点实现类 [{}] 不存在", className, e);
        }
    }
}
