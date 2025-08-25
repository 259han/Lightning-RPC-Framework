package com.rpc.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/**
 * 认证上下文
 * 
 * 包含认证后的用户/服务信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID（JWT认证时使用）
     */
    private String userId;
    
    /**
     * 服务ID（API密钥认证时使用）
     */
    private String serviceId;
    
    /**
     * 用户角色
     */
    private Set<String> roles;
    
    /**
     * 认证类型
     */
    private AuthType authType;
    
    /**
     * 认证时间
     */
    private LocalDateTime authenticatedAt;
    
    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;
    
    /**
     * 扩展属性
     */
    private java.util.Map<String, String> attributes;
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 检查是否有指定角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * 检查是否有任意一个指定角色
     */
    public boolean hasAnyRole(String... roles) {
        if (this.roles == null || roles == null) {
            return false;
        }
        return Arrays.stream(roles).anyMatch(this.roles::contains);
    }
    
    /**
     * 检查是否有所有指定角色
     */
    public boolean hasAllRoles(String... roles) {
        if (this.roles == null || roles == null) {
            return false;
        }
        return Arrays.stream(roles).allMatch(this.roles::contains);
    }
    
    /**
     * 添加角色
     */
    public void addRole(String role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }
    
    /**
     * 获取认证主体（用户ID或服务ID）
     */
    public String getPrincipal() {
        return authType == AuthType.JWT ? userId : serviceId;
    }
    
    /**
     * 获取扩展属性
     */
    public String getAttribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }
    
    /**
     * 设置扩展属性
     */
    public void setAttribute(String key, String value) {
        if (this.attributes == null) {
            this.attributes = new java.util.HashMap<>();
        }
        this.attributes.put(key, value);
    }
    
    /**
     * 创建JWT认证上下文
     */
    public static AuthContext forJwt(String userId, String[] roles, LocalDateTime expiresAt) {
        return AuthContext.builder()
                .userId(userId)
                .authType(AuthType.JWT)
                .roles(roles != null ? new HashSet<>(Arrays.asList(roles)) : new HashSet<>())
                .authenticatedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }
    
    /**
     * 创建API密钥认证上下文
     */
    public static AuthContext forApiKey(String serviceId, String[] roles, LocalDateTime expiresAt) {
        return AuthContext.builder()
                .serviceId(serviceId)
                .authType(AuthType.API_KEY)
                .roles(roles != null ? new HashSet<>(Arrays.asList(roles)) : new HashSet<>())
                .authenticatedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }
    
    @Override
    public String toString() {
        return String.format("AuthContext{type=%s, principal=%s, roles=%s, expired=%s}", 
                authType, getPrincipal(), roles, isExpired());
    }
}
