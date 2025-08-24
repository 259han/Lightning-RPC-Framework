package com.rpc.client;

import com.rpc.common.circuitbreaker.CircuitBreaker;
import com.rpc.common.circuitbreaker.CircuitBreakerManager;
import com.rpc.common.config.RpcConfig;
import com.rpc.common.exception.RpcException;
import com.rpc.common.retry.RetryPolicy;
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
        
        // 添加默认的日志追踪收集器
        this.traceManager.addCollector(new LogTraceCollector());
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
            
            // 开始链路追踪
            TraceContext trace = traceManager.startTrace(serviceName, methodName);
            trace.addTag("client.version", version);
            trace.addTag("client.group", group);
            
            try {
                // 检查熔断器
                CircuitBreaker circuitBreaker = null;
                if (config.isCircuitBreakerEnabled()) {
                    circuitBreaker = circuitBreakerManager.getCircuitBreaker(serviceName);
                    if (!circuitBreaker.allowRequest()) {
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
                        .build();
                
                // 执行带重试的调用
                Object result = executeWithRetry(request, circuitBreaker, serviceName, methodName);
                
                // 记录成功
                trace.addTag("result.status", "success");
                traceManager.finishTrace();
                
                return result;
                
            } catch (Throwable throwable) {
                // 记录失败
                trace.addTag("result.status", "error");
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
     * 关闭客户端
     */
    public void shutdown() {
        if (requestTransport != null) {
            requestTransport.close();
        }
        circuitBreakerManager.clear();
    }
}
