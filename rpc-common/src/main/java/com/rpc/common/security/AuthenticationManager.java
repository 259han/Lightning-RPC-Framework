package com.rpc.common.security;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 认证管理器
 * 
 * 功能：
 * 1. JWT Token验证
 * 2. API密钥管理
 * 3. 认证缓存管理
 */
@Slf4j
public class AuthenticationManager {
    
    private static volatile AuthenticationManager instance;
    
    private final JwtTokenProvider jwtTokenProvider;
    private final ApiKeyValidator apiKeyValidator;
    private final ConcurrentHashMap<String, AuthContext> authCache;
    private final ScheduledExecutorService cleanupExecutor;
    
    // 认证缓存过期时间（分钟）
    private final long cacheExpirationMinutes = 30;
    
    private AuthenticationManager() {
        this.jwtTokenProvider = new JwtTokenProvider();
        this.apiKeyValidator = new ApiKeyValidator();
        this.authCache = new ConcurrentHashMap<>();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuthCache-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // 定期清理过期的认证缓存
        startCacheCleanup();
        log.info("认证管理器已初始化");
    }
    
    public static AuthenticationManager getInstance() {
        if (instance == null) {
            synchronized (AuthenticationManager.class) {
                if (instance == null) {
                    instance = new AuthenticationManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * JWT Token认证
     */
    public AuthResult authenticateWithJwt(String token) {
        if (token == null || token.trim().isEmpty()) {
            return AuthResult.failure("Token不能为空");
        }
        
        try {
            // 检查缓存
            AuthContext cachedAuth = authCache.get(token);
            if (cachedAuth != null && !cachedAuth.isExpired()) {
                log.debug("使用缓存的JWT认证结果: {}", cachedAuth.getUserId());
                return AuthResult.success(cachedAuth);
            }
            
            // 验证JWT Token
            AuthContext authContext = jwtTokenProvider.validateToken(token);
            if (authContext != null) {
                // 缓存认证结果
                authCache.put(token, authContext);
                log.debug("JWT认证成功: {}", authContext.getUserId());
                return AuthResult.success(authContext);
            } else {
                log.warn("JWT Token验证失败: {}", token.substring(0, Math.min(20, token.length())));
                return AuthResult.failure("无效的JWT Token");
            }
            
        } catch (Exception e) {
            log.error("JWT认证异常", e);
            return AuthResult.failure("JWT认证异常: " + e.getMessage());
        }
    }
    
    /**
     * API密钥认证
     */
    public AuthResult authenticateWithApiKey(String apiKey, String serviceId) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return AuthResult.failure("API密钥不能为空");
        }
        
        try {
            String cacheKey = apiKey + ":" + serviceId;
            AuthContext cachedAuth = authCache.get(cacheKey);
            if (cachedAuth != null && !cachedAuth.isExpired()) {
                log.debug("使用缓存的API密钥认证结果: {}", cachedAuth.getServiceId());
                return AuthResult.success(cachedAuth);
            }
            
            // 验证API密钥
            AuthContext authContext = apiKeyValidator.validateApiKey(apiKey, serviceId);
            if (authContext != null) {
                // 缓存认证结果
                authCache.put(cacheKey, authContext);
                log.debug("API密钥认证成功: {}", authContext.getServiceId());
                return AuthResult.success(authContext);
            } else {
                log.warn("API密钥验证失败: service={}, key={}", serviceId, 
                    apiKey.substring(0, Math.min(8, apiKey.length())) + "***");
                return AuthResult.failure("无效的API密钥");
            }
            
        } catch (Exception e) {
            log.error("API密钥认证异常", e);
            return AuthResult.failure("API密钥认证异常: " + e.getMessage());
        }
    }
    
    /**
     * 生成JWT Token
     */
    public String generateJwtToken(String userId, String[] roles) {
        return jwtTokenProvider.generateToken(userId, roles);
    }
    
    /**
     * 生成API密钥
     */
    public String generateApiKey(String serviceId) {
        return apiKeyValidator.generateApiKey(serviceId);
    }
    
    /**
     * 清除认证缓存
     */
    public void clearAuthCache(String key) {
        authCache.remove(key);
        log.debug("清除认证缓存: {}", key);
    }
    
    /**
     * 获取缓存统计
     */
    public String getCacheStats() {
        long totalEntries = authCache.size();
        long expiredEntries = authCache.values().stream()
                .mapToLong(auth -> auth.isExpired() ? 1 : 0)
                .sum();
        
        return String.format("认证缓存统计 - 总数: %d, 过期: %d, 有效: %d", 
                totalEntries, expiredEntries, totalEntries - expiredEntries);
    }
    
    /**
     * 启动缓存清理任务
     */
    private void startCacheCleanup() {
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try {
                int removedCount = 0;
                var iterator = authCache.entrySet().iterator();
                
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (entry.getValue().isExpired()) {
                        iterator.remove();
                        removedCount++;
                    }
                }
                
                if (removedCount > 0) {
                    log.debug("清理过期认证缓存: {} 个", removedCount);
                }
                
            } catch (Exception e) {
                log.error("认证缓存清理异常", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * 关闭认证管理器
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("认证管理器已关闭");
    }
}
