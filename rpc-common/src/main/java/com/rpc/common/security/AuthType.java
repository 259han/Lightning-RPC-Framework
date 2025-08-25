package com.rpc.common.security;

/**
 * 认证类型枚举
 */
public enum AuthType {
    
    /**
     * JWT Token认证
     */
    JWT("JWT Token认证"),
    
    /**
     * API密钥认证
     */
    API_KEY("API密钥认证");
    
    private final String description;
    
    AuthType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
