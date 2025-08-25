package com.rpc.common.security;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API密钥验证器
 * 
 * 功能：
 * 1. API密钥生成
 * 2. API密钥验证
 * 3. 密钥管理
 */
@Slf4j
public class ApiKeyValidator {
    
    private final ConcurrentHashMap<String, ApiKeyInfo> apiKeys;
    private final SecureRandom secureRandom;
    
    // API密钥默认有效期（天）
    private final int defaultExpirationDays = 30;
    
    public ApiKeyValidator() {
        this.apiKeys = new ConcurrentHashMap<>();
        this.secureRandom = new SecureRandom();
        
        // 初始化一些默认的API密钥用于测试
        initializeDefaultApiKeys();
    }
    
    /**
     * 生成API密钥
     */
    public String generateApiKey(String serviceId) {
        return generateApiKey(serviceId, new String[]{"service"}, defaultExpirationDays);
    }
    
    /**
     * 生成API密钥（指定角色和过期时间）
     */
    public String generateApiKey(String serviceId, String[] roles, int expirationDays) {
        try {
            // 生成随机密钥
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            
            // 创建API密钥信息
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(expirationDays);
            ApiKeyInfo keyInfo = ApiKeyInfo.builder()
                    .serviceId(serviceId)
                    .roles(roles)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(expiresAt)
                    .enabled(true)
                    .build();
            
            // 存储密钥
            apiKeys.put(apiKey, keyInfo);
            
            log.info("生成API密钥: service={}, expires={}days", serviceId, expirationDays);
            return apiKey;
            
        } catch (Exception e) {
            log.error("生成API密钥失败", e);
            throw new RuntimeException("API密钥生成失败", e);
        }
    }
    
    /**
     * 验证API密钥
     */
    public AuthContext validateApiKey(String apiKey, String serviceId) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                return null;
            }
            
            ApiKeyInfo keyInfo = apiKeys.get(apiKey);
            if (keyInfo == null) {
                log.debug("API密钥不存在: {}", maskApiKey(apiKey));
                return null;
            }
            
            // 检查是否启用
            if (!keyInfo.isEnabled()) {
                log.warn("API密钥已禁用: service={}", keyInfo.getServiceId());
                return null;
            }
            
            // 检查服务ID匹配（如果指定）
            if (serviceId != null && !serviceId.equals(keyInfo.getServiceId())) {
                log.warn("API密钥服务ID不匹配: expected={}, actual={}", 
                        keyInfo.getServiceId(), serviceId);
                return null;
            }
            
            // 检查过期时间
            if (keyInfo.getExpiresAt() != null && LocalDateTime.now().isAfter(keyInfo.getExpiresAt())) {
                log.debug("API密钥已过期: service={}, expires={}", 
                        keyInfo.getServiceId(), keyInfo.getExpiresAt());
                return null;
            }
            
            // 构建AuthContext
            return AuthContext.forApiKey(keyInfo.getServiceId(), keyInfo.getRoles(), keyInfo.getExpiresAt());
            
        } catch (Exception e) {
            log.error("API密钥验证异常", e);
            return null;
        }
    }
    
    /**
     * 禁用API密钥
     */
    public boolean disableApiKey(String apiKey) {
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        if (keyInfo != null) {
            keyInfo.setEnabled(false);
            log.info("禁用API密钥: service={}", keyInfo.getServiceId());
            return true;
        }
        return false;
    }
    
    /**
     * 删除API密钥
     */
    public boolean removeApiKey(String apiKey) {
        ApiKeyInfo keyInfo = apiKeys.remove(apiKey);
        if (keyInfo != null) {
            log.info("删除API密钥: service={}", keyInfo.getServiceId());
            return true;
        }
        return false;
    }
    
    /**
     * 列出指定服务的所有API密钥
     */
    public java.util.List<String> listApiKeys(String serviceId) {
        return apiKeys.entrySet().stream()
                .filter(entry -> serviceId.equals(entry.getValue().getServiceId()))
                .filter(entry -> entry.getValue().isEnabled())
                .map(java.util.Map.Entry::getKey)
                .map(this::maskApiKey)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取API密钥统计
     */
    public String getApiKeyStats() {
        long totalKeys = apiKeys.size();
        long enabledKeys = apiKeys.values().stream()
                .mapToLong(info -> info.isEnabled() ? 1 : 0)
                .sum();
        long expiredKeys = apiKeys.values().stream()
                .mapToLong(info -> info.getExpiresAt() != null && 
                        LocalDateTime.now().isAfter(info.getExpiresAt()) ? 1 : 0)
                .sum();
        
        return String.format("API密钥统计 - 总数: %d, 启用: %d, 过期: %d", 
                totalKeys, enabledKeys, expiredKeys);
    }
    
    /**
     * 初始化默认API密钥
     */
    private void initializeDefaultApiKeys() {
        // 为测试生成一些默认密钥
        String testKey1 = generateApiKey("test-service-1", new String[]{"service", "read"}, 365);
        String testKey2 = generateApiKey("test-service-2", new String[]{"service", "write"}, 365);
        String adminKey = generateApiKey("admin-service", new String[]{"service", "admin"}, 365);
        
        log.info("初始化默认API密钥完成");
        log.info("测试密钥1: {} (test-service-1)", maskApiKey(testKey1));
        log.info("测试密钥2: {} (test-service-2)", maskApiKey(testKey2));
        log.info("管理员密钥: {} (admin-service)", maskApiKey(adminKey));
    }
    
    /**
     * 掩码API密钥用于日志显示
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 8) + "***" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * API密钥信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ApiKeyInfo {
        private String serviceId;
        private String[] roles;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private boolean enabled;
    }
}
