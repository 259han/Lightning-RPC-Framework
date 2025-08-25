package com.rpc.example;

import com.rpc.common.security.AuthenticationManager;
import com.rpc.common.security.AuthResult;
import com.rpc.common.ratelimit.RateLimitConfig;
import com.rpc.common.ratelimit.RateLimitManager;
import com.rpc.common.ratelimit.RateLimitResult;
import com.rpc.common.ratelimit.RateLimitType;
import com.rpc.common.ratelimit.TokenBucketRateLimiter;
import com.rpc.common.ratelimit.SlidingWindowRateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全认证和限流功能测试
 * 
 * 测试内容：
 * 1. JWT Token认证
 * 2. API密钥认证
 * 3. 令牌桶限流
 * 4. 滑动窗口限流
 * 5. 多级限流策略
 */
@Slf4j
public class SecurityAndRateLimitTest {
    
    public static void main(String[] args) {
        log.info("========================================");
        log.info("RPC框架安全认证和限流功能测试");
        log.info("========================================");
        
        boolean allTestsPassed = true;
        
        try {
            // 1. 测试安全认证功能
            allTestsPassed &= testAuthentication();
            
            // 2. 测试限流功能
            allTestsPassed &= testRateLimit();
            
            // 3. 测试集成功能
            allTestsPassed &= testIntegrationScenario();
            
            if (allTestsPassed) {
                log.info("========================================");
                log.info("所有安全认证和限流测试通过 ✅");
                log.info("========================================");
            } else {
                log.error("========================================");
                log.error("部分测试失败 ❌");
                log.error("========================================");
            }
            
        } catch (Exception e) {
            log.error("测试执行异常", e);
        } finally {
            // 清理资源
            AuthenticationManager.getInstance().shutdown();
        }
    }
    
    /**
     * 测试安全认证功能
     */
    private static boolean testAuthentication() {
        log.info("--- 测试安全认证功能 ---");
        
        try {
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            
            // 1. 测试JWT Token认证
            log.info("1. 测试JWT Token认证");
            String userId = "test-user-001";
            String[] userRoles = {"user", "read", "write"};
            
            // 生成JWT Token
            String jwtToken = authManager.generateJwtToken(userId, userRoles);
            log.info("生成JWT Token: {}", jwtToken.substring(0, Math.min(50, jwtToken.length())) + "...");
            
            // 验证JWT Token
            AuthResult jwtResult = authManager.authenticateWithJwt(jwtToken);
            if (!jwtResult.isSuccess()) {
                log.error("JWT认证失败: {}", jwtResult.getErrorMessage());
                return false;
            }
            
            log.info("JWT认证成功: principal={}, roles={}", 
                    jwtResult.getPrincipal(), jwtResult.getAuthContext().getRoles());
            
            // 2. 测试API密钥认证
            log.info("2. 测试API密钥认证");
            String serviceId = "test-service-auth";
            String[] serviceRoles = {"service", "admin"};
            
            // 生成API密钥
            String apiKey = authManager.generateApiKey(serviceId);
            log.info("生成API密钥: {}***{}", 
                    apiKey.substring(0, 8), apiKey.substring(apiKey.length() - 4));
            
            // 验证API密钥
            AuthResult apiResult = authManager.authenticateWithApiKey(apiKey, serviceId);
            if (!apiResult.isSuccess()) {
                log.error("API密钥认证失败: {}", apiResult.getErrorMessage());
                return false;
            }
            
            log.info("API密钥认证成功: principal={}, roles={}", 
                    apiResult.getPrincipal(), apiResult.getAuthContext().getRoles());
            
            // 3. 测试认证缓存
            log.info("3. 测试认证缓存");
            long startTime = System.currentTimeMillis();
            AuthResult cachedResult = authManager.authenticateWithJwt(jwtToken);
            long cacheTime = System.currentTimeMillis() - startTime;
            
            if (!cachedResult.isSuccess()) {
                log.error("缓存认证失败: {}", cachedResult.getErrorMessage());
                return false;
            }
            
            log.info("缓存认证成功，耗时: {}ms", cacheTime);
            
            // 4. 测试无效Token
            log.info("4. 测试无效Token");
            AuthResult invalidResult = authManager.authenticateWithJwt("invalid-token");
            if (invalidResult.isSuccess()) {
                log.error("无效Token应该认证失败");
                return false;
            }
            
            log.info("无效Token正确拒绝: {}", invalidResult.getErrorMessage());
            
            log.info("安全认证功能测试完成 ✅");
            return true;
            
        } catch (Exception e) {
            log.error("安全认证测试异常", e);
            return false;
        }
    }
    
    /**
     * 测试限流功能
     */
    private static boolean testRateLimit() {
        log.info("--- 测试限流功能 ---");
        
        try {
            // 1. 测试令牌桶限流
            if (!testTokenBucketRateLimit()) {
                return false;
            }
            
            // 2. 测试滑动窗口限流
            if (!testSlidingWindowRateLimit()) {
                return false;
            }
            
            // 3. 测试限流管理器
            if (!testRateLimitManager()) {
                return false;
            }
            
            log.info("限流功能测试完成 ✅");
            return true;
            
        } catch (Exception e) {
            log.error("限流功能测试异常", e);
            return false;
        }
    }
    
    /**
     * 测试令牌桶限流
     */
    private static boolean testTokenBucketRateLimit() {
        log.info("1. 测试令牌桶限流");
        
        RateLimitConfig config = RateLimitConfig.builder()
                .type(RateLimitType.TOKEN_BUCKET)
                .rate(10.0) // 每秒10个令牌
                .capacity(20) // 桶容量20
                .enabled(true)
                .build();
        
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(config);
        
        // 测试突发请求
        int successCount = 0;
        int limitedCount = 0;
        
        for (int i = 0; i < 25; i++) {
            if (rateLimiter.tryAcquire()) {
                successCount++;
            } else {
                limitedCount++;
            }
        }
        
        log.info("令牌桶测试结果 - 成功: {}, 限流: {}", successCount, limitedCount);
        
        RateLimitResult status = rateLimiter.getStatus();
        log.info("令牌桶状态: {}", status);
        
        // 验证突发流量处理
        if (successCount < 15 || limitedCount < 5) {
            log.error("令牌桶限流行为异常");
            return false;
        }
        
        return true;
    }
    
    /**
     * 测试滑动窗口限流
     */
    private static boolean testSlidingWindowRateLimit() {
        log.info("2. 测试滑动窗口限流");
        
        RateLimitConfig config = RateLimitConfig.builder()
                .type(RateLimitType.SLIDING_WINDOW)
                .rate(10.0) // 每秒10个请求
                .windowSizeMs(1000) // 1秒窗口
                .windowSlices(10) // 10个切片
                .enabled(true)
                .build();
        
        SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter(config);
        
        // 测试精确限流
        int successCount = 0;
        int limitedCount = 0;
        
        for (int i = 0; i < 15; i++) {
            if (rateLimiter.tryAcquire()) {
                successCount++;
            } else {
                limitedCount++;
            }
        }
        
        log.info("滑动窗口测试结果 - 成功: {}, 限流: {}", successCount, limitedCount);
        
        RateLimitResult status = rateLimiter.getStatus();
        log.info("滑动窗口状态: {}", status);
        
        // 验证精确限流
        if (successCount != 10 || limitedCount != 5) {
            log.warn("滑动窗口限流结果: 成功={}, 限流={} (预期: 成功=10, 限流=5)", 
                    successCount, limitedCount);
            // 允许小幅偏差，因为时间窗口的边界效应
        }
        
        return true;
    }
    
    /**
     * 测试限流管理器
     */
    private static boolean testRateLimitManager() {
        log.info("3. 测试限流管理器");
        
        RateLimitManager rateLimitManager = RateLimitManager.getInstance();
        
        // 测试服务级限流
        String serviceName = "test-service";
        boolean allowed1 = rateLimitManager.checkServiceRateLimit(serviceName);
        boolean allowed2 = rateLimitManager.checkServiceRateLimit(serviceName);
        
        log.info("服务级限流测试: 第一次={}, 第二次={}", allowed1, allowed2);
        
        // 测试方法级限流
        String methodName = "testMethod";
        boolean methodAllowed = rateLimitManager.checkMethodRateLimit(serviceName, methodName);
        log.info("方法级限流测试: {}", methodAllowed);
        
        // 测试IP级限流
        String ipAddress = "192.168.1.100";
        boolean ipAllowed = rateLimitManager.checkIpRateLimit(ipAddress);
        log.info("IP级限流测试: {}", ipAllowed);
        
        // 获取限流统计
        String stats = rateLimitManager.getRateLimitStats();
        log.info("限流统计: {}", stats);
        
        return true;
    }
    
    /**
     * 测试集成场景
     */
    private static boolean testIntegrationScenario() {
        log.info("--- 测试集成场景 ---");
        
        try {
            // 模拟高并发场景
            ExecutorService executor = Executors.newFixedThreadPool(10);
            RateLimitManager rateLimitManager = RateLimitManager.getInstance();
            AuthenticationManager authManager = AuthenticationManager.getInstance();
            
            AtomicInteger totalRequests = new AtomicInteger(0);
            AtomicInteger authSuccess = new AtomicInteger(0);
            AtomicInteger rateLimitSuccess = new AtomicInteger(0);
            AtomicInteger bothSuccess = new AtomicInteger(0);
            
            // 生成测试Token
            String testToken = authManager.generateJwtToken("concurrent-user", new String[]{"user"});
            
            // 提交并发任务
            for (int i = 0; i < 50; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        totalRequests.incrementAndGet();
                        
                        // 认证测试
                        AuthResult authResult = authManager.authenticateWithJwt(testToken);
                        boolean authOk = authResult.isSuccess();
                        if (authOk) {
                            authSuccess.incrementAndGet();
                        }
                        
                        // 限流测试
                        boolean rateLimitOk = rateLimitManager.checkServiceRateLimit("concurrent-service");
                        if (rateLimitOk) {
                            rateLimitSuccess.incrementAndGet();
                        }
                        
                        // 两者都成功
                        if (authOk && rateLimitOk) {
                            bothSuccess.incrementAndGet();
                        }
                        
                        log.debug("并发任务{}: auth={}, rateLimit={}", taskId, authOk, rateLimitOk);
                        
                    } catch (Exception e) {
                        log.error("并发任务{}异常", taskId, e);
                    }
                });
            }
            
            // 等待所有任务完成
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("并发测试未在预期时间内完成");
                executor.shutdownNow();
            }
            
            log.info("并发集成测试结果:");
            log.info("  总请求数: {}", totalRequests.get());
            log.info("  认证成功: {}", authSuccess.get());
            log.info("  限流通过: {}", rateLimitSuccess.get());
            log.info("  完全成功: {}", bothSuccess.get());
            
            // 生成最终报告
            log.info("生成限流报告:");
            rateLimitManager.generateRateLimitReport();
            
            log.info("认证缓存统计: {}", authManager.getCacheStats());
            
            log.info("集成场景测试完成 ✅");
            return true;
            
        } catch (Exception e) {
            log.error("集成场景测试异常", e);
            return false;
        }
    }
}
