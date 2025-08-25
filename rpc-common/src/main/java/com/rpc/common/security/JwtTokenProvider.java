package com.rpc.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token提供者
 * 
 * 简化版JWT实现，包含：
 * 1. Token生成
 * 2. Token验证
 * 3. Token解析
 */
@Slf4j
public class JwtTokenProvider {
    
    private static final String ALGORITHM = "HS256";
    private static final String SECRET_KEY = "rpc-framework-jwt-secret-key-2024";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Token默认有效期（小时）
    private final int defaultExpirationHours = 24;
    
    /**
     * 生成JWT Token
     */
    public String generateToken(String userId, String[] roles) {
        return generateToken(userId, roles, defaultExpirationHours);
    }
    
    /**
     * 生成JWT Token（指定过期时间）
     */
    public String generateToken(String userId, String[] roles, int expirationHours) {
        try {
            // Header
            Map<String, Object> header = new HashMap<>();
            header.put("alg", ALGORITHM);
            header.put("typ", "JWT");
            String headerJson = objectMapper.writeValueAsString(header);
            String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            
            // Payload
            long now = System.currentTimeMillis() / 1000;
            long expiration = now + (expirationHours * 3600);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", userId);  // Subject
            payload.put("iat", now);     // Issued At
            payload.put("exp", expiration); // Expiration
            payload.put("roles", roles);
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            
            // Signature
            String data = encodedHeader + "." + encodedPayload;
            String signature = generateSignature(data);
            
            String token = data + "." + signature;
            log.debug("生成JWT Token: user={}, expires={}h", userId, expirationHours);
            return token;
            
        } catch (Exception e) {
            log.error("生成JWT Token失败", e);
            throw new RuntimeException("JWT Token生成失败", e);
        }
    }
    
    /**
     * 验证JWT Token
     */
    public AuthContext validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return null;
            }
            
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("JWT Token格式无效: 部分数量不正确");
                return null;
            }
            
            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String signature = parts[2];
            
            // 验证签名
            String data = encodedHeader + "." + encodedPayload;
            String expectedSignature = generateSignature(data);
            if (!signature.equals(expectedSignature)) {
                log.warn("JWT Token签名验证失败");
                return null;
            }
            
            // 解析Payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(encodedPayload), 
                    StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            
            // 检查过期时间
            Number expNumber = (Number) payload.get("exp");
            if (expNumber != null) {
                long exp = expNumber.longValue();
                long now = System.currentTimeMillis() / 1000;
                if (now > exp) {
                    log.debug("JWT Token已过期: exp={}, now={}", exp, now);
                    return null;
                }
            }
            
            // 构建AuthContext
            String userId = (String) payload.get("sub");
            @SuppressWarnings("unchecked")
            java.util.List<String> rolesList = (java.util.List<String>) payload.get("roles");
            String[] roles = rolesList != null ? 
                    rolesList.toArray(new String[0]) : new String[0];
            
            LocalDateTime expiresAt = null;
            if (expNumber != null) {
                expiresAt = LocalDateTime.ofEpochSecond(expNumber.longValue(), 0, ZoneOffset.UTC);
            }
            
            return AuthContext.forJwt(userId, roles, expiresAt);
            
        } catch (Exception e) {
            log.error("JWT Token验证异常", e);
            return null;
        }
    }
    
    /**
     * 解析Token获取用户ID（不验证签名，仅用于调试）
     */
    public String parseUserId(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return null;
            }
            
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), 
                    StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            return (String) payload.get("sub");
            
        } catch (Exception e) {
            log.debug("解析JWT Token用户ID失败", e);
            return null;
        }
    }
    
    /**
     * 生成HMAC签名
     */
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
        } catch (Exception e) {
            throw new RuntimeException("生成JWT签名失败", e);
        }
    }
}
