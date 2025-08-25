package com.rpc.server.interceptor;

import com.rpc.common.security.AuthContext;
import com.rpc.common.security.AuthResult;
import com.rpc.common.security.AuthenticationManager;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全认证拦截器
 * 
 * 功能：
 * 1. JWT Token验证
 * 2. API密钥验证
 * 3. 权限检查
 */
@Slf4j
public class SecurityInterceptor implements RpcInterceptor {
    
    private final AuthenticationManager authManager;
    private final boolean enabled;
    
    public SecurityInterceptor() {
        this(true);
    }
    
    public SecurityInterceptor(boolean enabled) {
        this.authManager = AuthenticationManager.getInstance();
        this.enabled = enabled;
        log.info("安全认证拦截器已初始化，启用状态: {}", enabled);
    }
    
    @Override
    public boolean preProcess(RpcRequest request, RpcResponse<?> response) {
        if (!enabled) {
            return true;
        }
        
        try {
            // 检查是否需要认证
            if (!requiresAuthentication(request)) {
                log.debug("服务无需认证: {}", request.getInterfaceName());
                return true;
            }
            
            // 获取认证Token
            String authToken = request.getAuthToken();
            if (authToken == null || authToken.trim().isEmpty()) {
                log.warn("缺少认证Token: service={}, method={}", 
                        request.getInterfaceName(), request.getMethodName());
                setAuthenticationError(response, "缺少认证Token", "MISSING_TOKEN");
                return false;
            }
            
            // 执行认证
            AuthResult authResult = authenticate(authToken, request);
            if (!authResult.isSuccess()) {
                log.warn("认证失败: service={}, method={}, error={}", 
                        request.getInterfaceName(), request.getMethodName(), 
                        authResult.getErrorMessage());
                setAuthenticationError(response, authResult.getErrorMessage(), 
                        authResult.getErrorCode());
                return false;
            }
            
            // 权限检查
            if (!checkPermissions(authResult.getAuthContext(), request)) {
                log.warn("权限不足: service={}, method={}, principal={}", 
                        request.getInterfaceName(), request.getMethodName(), 
                        authResult.getPrincipal());
                setAuthenticationError(response, "权限不足", "INSUFFICIENT_PERMISSIONS");
                return false;
            }
            
            // 将认证上下文存储到请求中（用于后续处理）
            request.setAttribute("authContext", authResult.getAuthContext());
            
            log.debug("认证成功: service={}, method={}, principal={}", 
                    request.getInterfaceName(), request.getMethodName(), 
                    authResult.getPrincipal());
            
            return true;
            
        } catch (Exception e) {
            log.error("安全认证拦截器异常", e);
            setAuthenticationError(response, "认证系统异常", "AUTHENTICATION_ERROR");
            return false;
        }
    }
    
    @Override
    public void postProcess(RpcRequest request, RpcResponse<?> response) {
        // 清理认证上下文
        request.removeAttribute("authContext");
    }
    
    /**
     * 检查服务是否需要认证
     */
    private boolean requiresAuthentication(RpcRequest request) {
        // 默认所有服务都需要认证，除了特殊的公开服务
        String serviceName = request.getInterfaceName();
        
        // 公开服务列表（无需认证）
        return !isPublicService(serviceName);
    }
    
    /**
     * 检查是否为公开服务
     */
    private boolean isPublicService(String serviceName) {
        // 系统健康检查服务
        if (serviceName.contains("HealthCheck")) {
            return true;
        }
        
        // 系统信息服务
        if (serviceName.contains("SystemInfo")) {
            return true;
        }
        
        // 其他公开服务可以在这里添加
        return false;
    }
    
    /**
     * 执行认证
     */
    private AuthResult authenticate(String authToken, RpcRequest request) {
        // 首先尝试JWT认证
        if (authToken.contains(".")) {
            AuthResult jwtResult = authManager.authenticateWithJwt(authToken);
            if (jwtResult.isSuccess()) {
                return jwtResult;
            }
        }
        
        // 然后尝试API密钥认证
        String serviceId = extractServiceId(request);
        return authManager.authenticateWithApiKey(authToken, serviceId);
    }
    
    /**
     * 权限检查
     */
    private boolean checkPermissions(AuthContext authContext, RpcRequest request) {
        String serviceName = request.getInterfaceName();
        String methodName = request.getMethodName();
        
        // 基本权限检查：所有认证用户都有基本调用权限
        if (authContext.hasRole("service")) {
            return true;
        }
        
        // 管理员权限：可以访问所有服务
        if (authContext.hasRole("admin")) {
            return true;
        }
        
        // 只读权限：只能访问查询类方法
        if (authContext.hasRole("read") && isReadOnlyMethod(methodName)) {
            return true;
        }
        
        // 写权限：可以访问修改类方法
        if (authContext.hasRole("write")) {
            return true;
        }
        
        // 默认拒绝
        return false;
    }
    
    /**
     * 检查是否为只读方法
     */
    private boolean isReadOnlyMethod(String methodName) {
        String lowerMethod = methodName.toLowerCase();
        return lowerMethod.startsWith("get") || 
               lowerMethod.startsWith("query") || 
               lowerMethod.startsWith("find") || 
               lowerMethod.startsWith("list") || 
               lowerMethod.startsWith("search");
    }
    
    /**
     * 从请求中提取服务ID
     */
    private String extractServiceId(RpcRequest request) {
        // 可以从服务名称中提取服务ID，或者使用其他策略
        return request.getInterfaceName();
    }
    
    /**
     * 设置认证错误响应
     */
    private void setAuthenticationError(RpcResponse<?> response, String message, String code) {
        response.setCode(401); // Unauthorized
        response.setMessage("认证失败: " + message);
        response.setData(null);
        
        // 可以添加更多错误信息
        response.addExtension("errorCode", code);
        response.addExtension("errorType", "AUTHENTICATION_ERROR");
    }
}
