package com.rpc.client;

import com.rpc.common.circuitbreaker.CircuitBreaker;
import com.rpc.common.circuitbreaker.CircuitBreakerManager;
import com.rpc.common.config.RpcConfig;
import com.rpc.common.exception.RpcException;
import com.rpc.common.metrics.MetricsManager;
import com.rpc.common.retry.RetryPolicy;
import com.rpc.common.shutdown.GracefulShutdownManager;
import com.rpc.common.shutdown.MetricsShutdownHook;
import com.rpc.common.trace.TraceManager;
import com.rpc.common.trace.TraceContext;
import com.rpc.common.trace.LogTraceCollector;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RPC客户端 - 支持超时、重试、熔断器和链路追踪
 */
@Slf4j
public class RpcClient {
    private final RpcRequestTransport requestTransport;
    private final RpcConfig config;
    private final CircuitBreakerManager circuitBreakerManager;
    private final TraceManager traceManager;
    private String globalAuthToken; // 全局认证Token
    private final MetricsManager metricsManager;
    private final AsyncRpcClient asyncRpcClient;
    private final GracefulShutdownManager shutdownManager;
    
    // 兼容旧的构造函数
    public RpcClient(RpcRequestTransport requestTransport, long timeout) {
        this(requestTransport, RpcConfig.builder().requestTimeout(timeout).build());
    }
    
    public RpcClient(RpcRequestTransport requestTransport, RpcConfig config) {
        this.requestTransport = requestTransport;
        this.config = config;
        this.circuitBreakerManager = CircuitBreakerManager.getInstance();
        this.circuitBreakerManager.setConfig(config);
        this.traceManager = TraceManager.getInstance();
        this.metricsManager = MetricsManager.getInstance();
        this.shutdownManager = GracefulShutdownManager.getInstance();
        
        // 初始化异步RPC客户端
        this.asyncRpcClient = new AsyncRpcClient(requestTransport, config);
        
        // 添加默认的日志追踪收集器
        this.traceManager.addCollector(new LogTraceCollector());
        
        // 注册关闭钩子
        this.shutdownManager.registerShutdownHook(new RpcClientShutdownHook(this, "DefaultRpcClient"));
        this.shutdownManager.registerShutdownHook(new MetricsShutdownHook(metricsManager));
        
        // 注意：不自动启用定期报告，由用户根据需要手动启用
        // 如果需要启用定期报告，可以调用：metricsManager.enableReporting(30)
        
        log.info("RpcClient初始化完成，支持异步调用和优雅关闭");
    }
    
    /**
     * 创建代理对象
     *
     * @param clazz   接口类
     * @param version 版本号
     * @param group   分组
     * @param <T>     接口类型
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz, String version, String group) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                new RpcInvocationHandler(clazz, version, group)
        );
    }
    
    /**
     * RPC调用处理器 - 增强版，支持高级功能
     */
    private class RpcInvocationHandler implements InvocationHandler {
        private final Class<?> interfaceClass;
        private final String version;
        private final String group;
        
        public RpcInvocationHandler(Class<?> interfaceClass, String version, String group) {
            this.interfaceClass = interfaceClass;
            this.version = version;
            this.group = group;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理Object基础方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            
            String serviceName = interfaceClass.getName();
            String methodName = method.getName();
            
            // 开始链路追踪和性能监控
            TraceContext trace = traceManager.startTrace(serviceName, methodName);
            trace.addTag("client.version", version);
            trace.addTag("client.group", group);
            
            long startTime = System.currentTimeMillis();
            metricsManager.recordThroughput(serviceName, methodName);
            
            try {
                // 检查熔断器
                CircuitBreaker circuitBreaker = null;
                if (config.isCircuitBreakerEnabled()) {
                    circuitBreaker = circuitBreakerManager.getCircuitBreaker(serviceName);
                    if (!circuitBreaker.allowRequest()) {
                        metricsManager.recordError(serviceName, methodName, 
                            new RpcException("服务熔断器开启"));
                        throw new RpcException("服务 [" + serviceName + "] 熔断器开启，拒绝请求");
                    }
                }
                
                // 构建RPC请求
                RpcRequest request = RpcRequest.builder()
                        .interfaceName(serviceName)
                        .methodName(methodName)
                        .parameterTypes(method.getParameterTypes())
                        .parameters(args)
                        .version(version)
                        .group(group)
                        .authToken(globalAuthToken) // 设置认证Token
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                // 执行带重试的调用
                Object result = executeWithRetry(request, circuitBreaker, serviceName, methodName);
                
                // 记录成功和响应时间
                long duration = System.currentTimeMillis() - startTime;
                metricsManager.recordSuccess(serviceName, methodName);
                metricsManager.recordRequestTime(serviceName, methodName, duration);
                
                trace.addTag("result.status", "success");
                trace.addTag("response.time", String.valueOf(duration) + "ms");
                traceManager.finishTrace();
                
                return result;
                
            } catch (Throwable throwable) {
                // 记录失败和响应时间
                long duration = System.currentTimeMillis() - startTime;
                metricsManager.recordError(serviceName, methodName, throwable);
                metricsManager.recordRequestTime(serviceName, methodName, duration);
                
                trace.addTag("result.status", "error");
                trace.addTag("response.time", String.valueOf(duration) + "ms");
                trace.addTag("error.message", throwable.getMessage());
                traceManager.finishTraceWithError(throwable);
                throw throwable;
            }
        }
        
        /**
         * 执行带重试的RPC调用
         */
        private Object executeWithRetry(RpcRequest rpcRequest, CircuitBreaker circuitBreaker, 
                                      String serviceName, String methodName) throws Throwable {
            RetryPolicy retryPolicy = config.getRetryPolicy();
            int retryCount = 0;
            Throwable lastException = null;
            
            while (true) {
                try {
                    // 执行RPC调用
                    Object result = executeRpcCall(rpcRequest);
                    
                    // 记录成功
                    if (circuitBreaker != null) {
                        circuitBreaker.recordSuccess();
                    }
                    
                    if (retryCount > 0) {
                        log.info("服务 [{}#{}] 重试第{}次成功", serviceName, methodName, retryCount);
                    }
                    
                    return result;
                    
                } catch (Throwable throwable) {
                    lastException = throwable;
                    
                    // 记录失败
                    if (circuitBreaker != null) {
                        circuitBreaker.recordFailure();
                    }
                    
                    // 检查是否需要重试
                    if (!config.isRetryEnabled() || !retryPolicy.shouldRetry(retryCount, throwable)) {
                        log.error("服务 [{}#{}] 调用失败，不再重试", serviceName, methodName, throwable);
                        throw throwable;
                    }
                    
                    retryCount++;
                    long delay = retryPolicy.getRetryDelay(retryCount);
                    
                    log.warn("服务 [{}#{}] 调用失败，{}ms后进行第{}次重试: {}", 
                            serviceName, methodName, delay, retryCount, throwable.getMessage());
                    
                    // 等待重试延迟
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RpcException("重试等待被中断", e);
                        }
                    }
                }
            }
        }
        
        /**
         * 执行单次RPC调用
         */
        private Object executeRpcCall(RpcRequest rpcRequest) throws Throwable {
            CompletableFuture<RpcResponse<Object>> future = requestTransport.sendRequest(rpcRequest);
            
            try {
                // 等待响应，应用超时配置
                RpcResponse<Object> response = future.get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
                
                if (response.getCode() != 200) {
                    throw new RpcException("RPC调用失败: " + response.getMessage());
                }
                
                return response.getData();
                
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RpcException("RPC调用超时 (" + config.getRequestTimeout() + "ms)", e);
            } catch (Exception e) {
                throw new RpcException("RPC调用异常", e);
            }
        }
    }
    
    /**
     * 获取配置
     */
    public RpcConfig getConfig() {
        return config;
    }
    
    /**
     * 获取熔断器管理器
     */
    public CircuitBreakerManager getCircuitBreakerManager() {
        return circuitBreakerManager;
    }
    
    /**
     * 获取指标管理器
     */
    public MetricsManager getMetricsManager() {
        return metricsManager;
    }
    
    /**
     * 获取性能指标快照
     */
    public com.rpc.common.metrics.MetricsSnapshot getMetricsSnapshot(String serviceName) {
        return metricsManager.getSnapshot(serviceName);
    }
    
    /**
     * 获取所有服务的性能指标快照
     */
    public com.rpc.common.metrics.MetricsSnapshot getAllMetricsSnapshot() {
        return metricsManager.getAllSnapshot();
    }
    
    /**
     * 手动生成性能报告
     */
    public void generateMetricsReport() {
        metricsManager.generateManualReport();
    }
    
    /**
     * 获取异步RPC客户端
     */
    public AsyncRpcClient getAsyncClient() {
        return asyncRpcClient;
    }
    
    /**
     * 异步调用RPC方法
     */
    public <T> CompletableFuture<RpcResponse<T>> asyncCall(RpcRequest request) {
        return asyncRpcClient.asyncCall(request);
    }
    
    /**
     * 批量调用RPC方法
     */
    public <T> CompletableFuture<java.util.List<RpcResponse<T>>> batchCall(java.util.List<RpcRequest> requests) {
        return asyncRpcClient.batchCall(requests);
    }
    
    /**
     * 获取异步调用统计信息
     */
    public AsyncRpcClient.AsyncCallStats getAsyncStats() {
        return asyncRpcClient.getStats();
    }
    
    /**
     * 启用优雅关闭
     */
    public void enableGracefulShutdown() {
        log.info("已启用优雅关闭，注册了 {} 个关闭钩子", shutdownManager.getShutdownHookCount());
    }
    
    /**
     * 手动触发优雅关闭
     */
    public void gracefulShutdown() {
        shutdownManager.shutdown();
    }
    
    /**
     * 关闭客户端
     */
    public void shutdown() {
        log.info("开始关闭RpcClient...");
        
        // 关闭异步客户端
        if (asyncRpcClient != null) {
            asyncRpcClient.close();
        }
        
        // 关闭传输层
        if (requestTransport != null) {
            requestTransport.close();
        }
        
        // 清理熔断器
        circuitBreakerManager.clear();
        
        log.info("RpcClient已关闭");
        // 注意：不关闭全局的MetricsManager和ShutdownManager，因为可能被其他客户端使用
    }
    
    /**
     * 设置全局认证Token
     */
    public void setGlobalAuthToken(String authToken) {
        this.globalAuthToken = authToken;
        log.info("设置全局认证Token: {}...", 
                authToken != null ? authToken.substring(0, Math.min(10, authToken.length())) : "null");
    }
    
    /**
     * 获取全局认证Token
     */
    public String getGlobalAuthToken() {
        return globalAuthToken;
    }
}
