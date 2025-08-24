package com.rpc.example;

import com.rpc.client.RpcClient;
import com.rpc.common.circuitbreaker.CircuitBreaker;
import com.rpc.common.config.FileConfigCenter;
import com.rpc.common.config.RpcConfig;
import com.rpc.common.retry.DefaultRetryPolicy;
import com.rpc.common.trace.TraceManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 第四阶段高级功能测试
 * 
 * 测试内容：
 * 1. 超时和重试功能
 * 2. 熔断器模式
 * 3. 配置中心集成
 * 4. 链路追踪功能
 */
@Slf4j
public class AdvancedFeaturesTest {
    
    public static void main(String[] args) {
        log.info("=== RPC框架第四阶段高级功能测试开始 ===");
        
        boolean allTestsPassed = true;
        
        try {
            // 1. 测试配置中心
            allTestsPassed &= testConfigCenter();
            
            // 2. 测试RPC配置
            allTestsPassed &= testRpcConfig();
            
            // 3. 测试重试策略
            allTestsPassed &= testRetryPolicy();
            
            // 4. 测试熔断器
            allTestsPassed &= testCircuitBreaker();
            
            // 5. 测试链路追踪
            allTestsPassed &= testTracing();
            
            if (allTestsPassed) {
                log.info("=== 所有高级功能测试通过 ✅ ===");
            } else {
                log.error("=== 部分高级功能测试失败 ❌ ===");
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("高级功能测试异常", e);
            System.exit(1);
        }
    }
    
    private static boolean testConfigCenter() {
        log.info("--- 测试配置中心功能 ---");
        
        try {
            FileConfigCenter configCenter = new FileConfigCenter("test-config.properties");
            
            // 测试设置和获取配置
            configCenter.setConfig("test.key", "test.value");
            String value = configCenter.getConfig("test.key");
            if (!"test.value".equals(value)) {
                log.error("配置中心设置/获取失败");
                return false;
            }
            
            // 测试默认值
            String defaultValue = configCenter.getConfig("non.exist.key", "default");
            if (!"default".equals(defaultValue)) {
                log.error("配置中心默认值获取失败");
                return false;
            }
            
            // 测试配置监听器
            final boolean[] listenerCalled = {false};
            configCenter.addConfigListener("test.key", (key, oldValue, newValue) -> {
                log.info("配置变更通知: {} = {} -> {}", key, oldValue, newValue);
                listenerCalled[0] = true;
            });
            
            configCenter.setConfig("test.key", "new.value");
            Thread.sleep(100); // 等待监听器执行
            
            if (!listenerCalled[0]) {
                log.error("配置变更监听器未触发");
                return false;
            }
            
            configCenter.shutdown();
            log.info("配置中心功能测试通过");
            return true;
            
        } catch (Exception e) {
            log.error("配置中心测试异常", e);
            return false;
        }
    }
    
    private static boolean testRpcConfig() {
        log.info("--- 测试RPC配置功能 ---");
        
        try {
            // 测试默认配置
            RpcConfig defaultConfig = RpcConfig.defaultConfig();
            if (defaultConfig.getRequestTimeout() != 5000) {
                log.error("默认配置请求超时时间不正确");
                return false;
            }
            
            // 测试高性能配置
            RpcConfig highPerfConfig = RpcConfig.highPerformanceConfig();
            if (highPerfConfig.getRequestTimeout() != 3000 || 
                !"protobuf".equals(highPerfConfig.getSerializationType())) {
                log.error("高性能配置不正确");
                return false;
            }
            
            // 测试高可用配置
            RpcConfig highAvailConfig = RpcConfig.highAvailabilityConfig();
            if (!highAvailConfig.isCircuitBreakerEnabled() || !highAvailConfig.isRetryEnabled()) {
                log.error("高可用配置不正确");
                return false;
            }
            
            // 测试自定义配置
            RpcConfig customConfig = RpcConfig.builder()
                    .requestTimeout(8000)
                    .retryEnabled(false)
                    .serializationType("json")
                    .build();
            
            if (customConfig.getRequestTimeout() != 8000 || customConfig.isRetryEnabled()) {
                log.error("自定义配置不正确");
                return false;
            }
            
            log.info("RPC配置功能测试通过");
            return true;
            
        } catch (Exception e) {
            log.error("RPC配置测试异常", e);
            return false;
        }
    }
    
    private static boolean testRetryPolicy() {
        log.info("--- 测试重试策略功能 ---");
        
        try {
            // 测试指数退避重试策略
            DefaultRetryPolicy exponentialPolicy = DefaultRetryPolicy.exponentialBackoff(3, 1000, 2.0, 10000);
            
            // 测试是否应该重试
            Exception networkException = new java.net.ConnectException("Connection refused");
            if (!exponentialPolicy.shouldRetry(0, networkException)) {
                log.error("网络异常应该重试");
                return false;
            }
            
            Exception businessException = new IllegalArgumentException("Invalid parameter");
            if (exponentialPolicy.shouldRetry(0, businessException)) {
                log.error("业务异常不应该重试");
                return false;
            }
            
            // 测试超过最大重试次数
            if (exponentialPolicy.shouldRetry(3, networkException)) {
                log.error("超过最大重试次数还要重试");
                return false;
            }
            
            // 测试重试延迟
            long delay1 = exponentialPolicy.getRetryDelay(1);
            long delay2 = exponentialPolicy.getRetryDelay(2);
            if (delay2 <= delay1) {
                log.error("指数退避延迟时间不正确");
                return false;
            }
            
            log.info("重试策略功能测试通过");
            return true;
            
        } catch (Exception e) {
            log.error("重试策略测试异常", e);
            return false;
        }
    }
    
    private static boolean testCircuitBreaker() {
        log.info("--- 测试熔断器功能 ---");
        
        try {
            CircuitBreaker circuitBreaker = new CircuitBreaker("test-service", 3, 5000);
            
            // 初始状态应该是CLOSED
            if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
                log.error("熔断器初始状态不正确");
                return false;
            }
            
            // 应该允许请求
            if (!circuitBreaker.allowRequest()) {
                log.error("CLOSED状态应该允许请求");
                return false;
            }
            
            // 记录失败，但未达到阈值
            circuitBreaker.recordFailure();
            circuitBreaker.recordFailure();
            if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
                log.error("未达到阈值时状态不应该改变");
                return false;
            }
            
            // 达到失败阈值，状态应该变为OPEN
            circuitBreaker.recordFailure();
            if (circuitBreaker.getState() != CircuitBreaker.State.OPEN) {
                log.error("达到失败阈值后状态应该变为OPEN");
                return false;
            }
            
            // OPEN状态不应该允许请求
            if (circuitBreaker.allowRequest()) {
                log.error("OPEN状态不应该允许请求");
                return false;
            }
            
            // 测试强制重置
            circuitBreaker.forceReset();
            if (circuitBreaker.getState() != CircuitBreaker.State.CLOSED) {
                log.error("强制重置后状态应该为CLOSED");
                return false;
            }
            
            log.info("熔断器功能测试通过");
            return true;
            
        } catch (Exception e) {
            log.error("熔断器测试异常", e);
            return false;
        }
    }
    
    private static boolean testTracing() {
        log.info("--- 测试链路追踪功能 ---");
        
        try {
            TraceManager traceManager = TraceManager.getInstance();
            
            // 开始追踪
            traceManager.startTrace("TestService", "testMethod");
            
            // 添加标签和日志
            traceManager.addTag("user.id", "12345");
            traceManager.addLog("request.size", 1024);
            
            // 模拟处理时间
            Thread.sleep(10);
            
            // 完成追踪
            traceManager.finishTrace();
            
            // 测试子追踪
            traceManager.startTrace("ParentService", "parentMethod");
            String parentTraceId = traceManager.getCurrentTrace().getTraceId();
            
            traceManager.startChildTrace("ChildService", "childMethod");
            String childTraceId = traceManager.getCurrentTrace().getTraceId();
            
            // 父子追踪应该有相同的traceId
            if (!parentTraceId.equals(childTraceId)) {
                log.error("父子追踪的traceId应该相同");
                return false;
            }
            
            traceManager.finishTrace();
            traceManager.finishTrace();
            
            // 测试错误追踪
            traceManager.startTrace("ErrorService", "errorMethod");
            traceManager.finishTraceWithError("Test error message");
            
            log.info("链路追踪功能测试通过");
            return true;
            
        } catch (Exception e) {
            log.error("链路追踪测试异常", e);
            return false;
        }
    }
}
