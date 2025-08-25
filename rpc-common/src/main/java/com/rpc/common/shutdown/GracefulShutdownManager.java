package com.rpc.common.shutdown;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 优雅关闭管理器
 * 
 * 管理应用程序的优雅关闭流程，确保所有资源正确清理
 */
@Slf4j
public class GracefulShutdownManager {
    
    private static final GracefulShutdownManager INSTANCE = new GracefulShutdownManager();
    
    private final List<ShutdownHook> shutdownHooks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final AtomicBoolean jvmHookRegistered = new AtomicBoolean(false);
    private volatile long shutdownTimeout = 30000; // 30秒默认超时
    
    private GracefulShutdownManager() {
        registerJvmShutdownHook();
    }
    
    public static GracefulShutdownManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册关闭钩子
     */
    public void registerShutdownHook(ShutdownHook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("ShutdownHook不能为null");
        }
        
        shutdownHooks.add(hook);
        log.debug("已注册关闭钩子: {} (优先级: {})", hook.getName(), hook.getPriority());
        
        // 按优先级排序（数字越小优先级越高）
        shutdownHooks.sort((h1, h2) -> Integer.compare(h1.getPriority(), h2.getPriority()));
    }
    
    /**
     * 移除关闭钩子
     */
    public void removeShutdownHook(ShutdownHook hook) {
        if (shutdownHooks.remove(hook)) {
            log.debug("已移除关闭钩子: {}", hook.getName());
        }
    }
    
    /**
     * 设置关闭超时时间
     */
    public void setShutdownTimeout(long timeoutMs) {
        this.shutdownTimeout = timeoutMs;
        log.debug("设置关闭超时时间: {}ms", timeoutMs);
    }
    
    /**
     * 执行优雅关闭
     */
    public void shutdown() {
        if (!shutdownInitiated.compareAndSet(false, true)) {
            log.warn("关闭流程已经在进行中，忽略重复调用");
            return;
        }
        
        log.info("开始执行优雅关闭流程...");
        log.info("共有 {} 个关闭钩子需要执行", shutdownHooks.size());
        
        long startTime = System.currentTimeMillis();
        CountDownLatch completionLatch = new CountDownLatch(shutdownHooks.size());
        
        // 执行所有关闭钩子
        for (ShutdownHook hook : shutdownHooks) {
            executeShutdownHook(hook, completionLatch);
        }
        
        // 等待所有钩子完成或超时
        try {
            boolean completed = completionLatch.await(shutdownTimeout, TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - startTime;
            
            if (completed) {
                log.info("优雅关闭完成，耗时: {}ms", duration);
            } else {
                log.warn("优雅关闭超时，耗时: {}ms，可能有钩子未完成", duration);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("优雅关闭被中断", e);
        }
        
        log.info("优雅关闭流程结束");
    }
    
    /**
     * 执行单个关闭钩子
     */
    private void executeShutdownHook(ShutdownHook hook, CountDownLatch latch) {
        Thread hookThread = new Thread(() -> {
            try {
                log.debug("执行关闭钩子: {} (优先级: {})", hook.getName(), hook.getPriority());
                long hookStart = System.currentTimeMillis();
                
                hook.shutdown();
                
                long hookDuration = System.currentTimeMillis() - hookStart;
                log.debug("关闭钩子 {} 执行完成，耗时: {}ms", hook.getName(), hookDuration);
                
            } catch (Exception e) {
                log.error("关闭钩子 {} 执行失败", hook.getName(), e);
            } finally {
                latch.countDown();
            }
        }, "ShutdownHook-" + hook.getName());
        
        hookThread.setDaemon(false); // 确保JVM等待关闭钩子完成
        hookThread.start();
    }
    
    /**
     * 注册JVM关闭钩子
     */
    private void registerJvmShutdownHook() {
        if (jvmHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!shutdownInitiated.get()) {
                    log.info("检测到JVM关闭，触发优雅关闭流程");
                    shutdown();
                }
            }, "GracefulShutdown-JVM-Hook"));
            
            log.debug("已注册JVM关闭钩子");
        }
    }
    
    /**
     * 检查是否正在关闭
     */
    public boolean isShuttingDown() {
        return shutdownInitiated.get();
    }
    
    /**
     * 获取已注册的关闭钩子数量
     */
    public int getShutdownHookCount() {
        return shutdownHooks.size();
    }
    
    /**
     * 获取关闭钩子列表（只读）
     */
    public List<String> getShutdownHookNames() {
        return shutdownHooks.stream()
                .map(ShutdownHook::getName)
                .toList();
    }
    
    /**
     * 强制关闭（仅用于紧急情况）
     */
    public void forceShutdown() {
        log.warn("执行强制关闭");
        shutdownInitiated.set(true);
        
        // 快速执行所有钩子，不等待完成
        for (ShutdownHook hook : shutdownHooks) {
            try {
                hook.shutdown();
                log.debug("强制执行关闭钩子: {}", hook.getName());
            } catch (Exception e) {
                log.error("强制关闭钩子 {} 失败", hook.getName(), e);
            }
        }
        
        log.warn("强制关闭完成");
    }
}
