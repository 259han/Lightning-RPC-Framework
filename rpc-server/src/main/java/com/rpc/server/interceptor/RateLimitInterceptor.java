package com.rpc.server.interceptor;

import com.rpc.common.ratelimit.RateLimitManager;
import com.rpc.common.ratelimit.RateLimitResult;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流拦截器
 * 
 * 功能：
 * 1. 服务级别限流
 * 2. 方法级别限流
 * 3. IP级别限流
 * 4. 用户级别限流
 */
@Slf4j
public class RateLimitInterceptor implements RpcInterceptor {
    
    private final RateLimitManager rateLimitManager;
    private final boolean enabled;
    private final boolean enableServiceLimit;
    private final boolean enableMethodLimit;
    private final boolean enableIpLimit;
    private final boolean enableUserLimit;
    
    public RateLimitInterceptor() {
        this(true, true, true, true, false);
    }
    
    public RateLimitInterceptor(boolean enabled, boolean enableServiceLimit, 
                              boolean enableMethodLimit, boolean enableIpLimit, 
                              boolean enableUserLimit) {
        this.rateLimitManager = RateLimitManager.getInstance();
        this.enabled = enabled;
        this.enableServiceLimit = enableServiceLimit;
        this.enableMethodLimit = enableMethodLimit;
        this.enableIpLimit = enableIpLimit;
        this.enableUserLimit = enableUserLimit;
        
        log.info("限流拦截器已初始化 - 启用: {}, 服务限流: {}, 方法限流: {}, IP限流: {}, 用户限流: {}", 
                enabled, enableServiceLimit, enableMethodLimit, enableIpLimit, enableUserLimit);
    }
    
    @Override
    public boolean preProcess(RpcRequest request, RpcResponse<?> response) {
        if (!enabled) {
            return true;
        }
        
        try {
            String serviceName = request.getInterfaceName();
            String methodName = request.getMethodName();
            String clientIp = request.getClientIp();
            
            // 1. IP级别限流检查
            if (enableIpLimit && clientIp != null) {
                if (!rateLimitManager.checkIpRateLimit(clientIp)) {
                    setRateLimitError(response, "IP限流", "IP_RATE_LIMITED", clientIp);
                    return false;
                }
            }
            
            // 2. 用户级别限流检查
            if (enableUserLimit) {
                String userId = extractUserId(request);
                if (userId != null && !rateLimitManager.checkUserRateLimit(userId)) {
                    setRateLimitError(response, "用户限流", "USER_RATE_LIMITED", userId);
                    return false;
                }
            }
            
            // 3. 服务级别限流检查
            if (enableServiceLimit) {
                if (!rateLimitManager.checkServiceRateLimit(serviceName)) {
                    setRateLimitError(response, "服务限流", "SERVICE_RATE_LIMITED", serviceName);
                    return false;
                }
            }
            
            // 4. 方法级别限流检查
            if (enableMethodLimit) {
                if (!rateLimitManager.checkMethodRateLimit(serviceName, methodName)) {
                    setRateLimitError(response, "方法限流", "METHOD_RATE_LIMITED", 
                            serviceName + "#" + methodName);
                    return false;
                }
            }
            
            // 记录限流状态（用于监控）
            recordRateLimitStatus(request);
            
            return true;
            
        } catch (Exception e) {
            log.error("限流拦截器异常", e);
            // 限流异常时允许请求通过，避免影响正常业务
            return true;
        }
    }
    
    @Override
    public void postProcess(RpcRequest request, RpcResponse<?> response) {
        // 可以在这里记录请求完成后的统计信息
        if (enabled) {
            recordPostProcessMetrics(request, response);
        }
    }
    
    /**
     * 从请求中提取用户ID
     */
    private String extractUserId(RpcRequest request) {
        // 从认证上下文中获取用户ID
        Object authContext = request.getAttribute("authContext");
        if (authContext instanceof com.rpc.common.security.AuthContext) {
            com.rpc.common.security.AuthContext auth = 
                    (com.rpc.common.security.AuthContext) authContext;
            return auth.getUserId();
        }
        
        // 如果没有认证上下文，可以从其他地方获取用户标识
        return null;
    }
    
    /**
     * 记录限流状态
     */
    private void recordRateLimitStatus(RpcRequest request) {
        try {
            String serviceName = request.getInterfaceName();
            String methodName = request.getMethodName();
            
            // 记录服务级别状态
            if (enableServiceLimit) {
                RateLimitResult serviceStatus = rateLimitManager.getRateLimitStatus("service:" + serviceName);
                if (serviceStatus != null && serviceStatus.needsAlert()) {
                    log.warn("服务限流告警: service={}, {}", serviceName, serviceStatus);
                }
            }
            
            // 记录方法级别状态
            if (enableMethodLimit) {
                String methodKey = "method:" + serviceName + "#" + methodName;
                RateLimitResult methodStatus = rateLimitManager.getRateLimitStatus(methodKey);
                if (methodStatus != null && methodStatus.needsAlert()) {
                    log.warn("方法限流告警: method={}, {}", methodKey, methodStatus);
                }
            }
            
        } catch (Exception e) {
            log.debug("记录限流状态异常", e);
        }
    }
    
    /**
     * 记录请求完成后的指标
     */
    private void recordPostProcessMetrics(RpcRequest request, RpcResponse<?> response) {
        try {
            // 可以记录请求成功率、响应时间等指标
            boolean success = response.getCode() == 200;
            String serviceName = request.getInterfaceName();
            String methodName = request.getMethodName();
            
            log.debug("RPC请求完成: service={}, method={}, success={}, code={}", 
                    serviceName, methodName, success, response.getCode());
            
        } catch (Exception e) {
            log.debug("记录后处理指标异常", e);
        }
    }
    
    /**
     * 设置限流错误响应
     */
    private void setRateLimitError(RpcResponse<?> response, String limitType, 
                                 String errorCode, String target) {
        response.setCode(429); // Too Many Requests
        response.setMessage(String.format("%s: %s", limitType, target));
        response.setData(null);
        
        // 添加扩展信息
        response.addExtension("errorCode", errorCode);
        response.addExtension("errorType", "RATE_LIMIT_ERROR");
        response.addExtension("limitTarget", target);
        response.addExtension("retryAfter", "1000"); // 建议1秒后重试
        
        log.warn("触发限流: type={}, target={}", limitType, target);
    }
    
    /**
     * 获取限流统计报告
     */
    public String getRateLimitReport() {
        return rateLimitManager.getRateLimitStats();
    }
    
    /**
     * 重置所有限流器
     */
    public void resetAllRateLimiters() {
        rateLimitManager.resetAllRateLimiters();
        log.info("已重置所有限流器");
    }
}
