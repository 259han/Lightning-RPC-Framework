package com.rpc.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 认证结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 认证是否成功
     */
    private boolean success;
    
    /**
     * 认证上下文（成功时包含）
     */
    private AuthContext authContext;
    
    /**
     * 错误消息（失败时包含）
     */
    private String errorMessage;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 创建成功的认证结果
     */
    public static AuthResult success(AuthContext authContext) {
        return new AuthResult(true, authContext, null, null);
    }
    
    /**
     * 创建失败的认证结果
     */
    public static AuthResult failure(String errorMessage) {
        return new AuthResult(false, null, errorMessage, null);
    }
    
    /**
     * 创建失败的认证结果（带错误代码）
     */
    public static AuthResult failure(String errorMessage, String errorCode) {
        return new AuthResult(false, null, errorMessage, errorCode);
    }
    
    /**
     * 获取认证主体
     */
    public String getPrincipal() {
        return success && authContext != null ? authContext.getPrincipal() : null;
    }
    
    /**
     * 检查是否有指定角色
     */
    public boolean hasRole(String role) {
        return success && authContext != null && authContext.hasRole(role);
    }
    
    /**
     * 检查是否有任意一个指定角色
     */
    public boolean hasAnyRole(String... roles) {
        return success && authContext != null && authContext.hasAnyRole(roles);
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("AuthResult{success=true, principal=%s}", getPrincipal());
        } else {
            return String.format("AuthResult{success=false, error=%s, code=%s}", 
                    errorMessage, errorCode);
        }
    }
}
