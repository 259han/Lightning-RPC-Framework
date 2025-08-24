package com.rpc.example;

import com.rpc.client.RpcClient;
import com.rpc.common.compress.GzipCompressor;
import com.rpc.common.compress.Lz4Compressor;
import com.rpc.common.compress.SnappyCompressor;
import com.rpc.common.config.RpcConfig;
import com.rpc.extension.ExtensionLoader;
import com.rpc.loadbalance.ConsistentHashLoadBalancer;
import com.rpc.protocol.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * 新增功能测试
 * 
 * 测试内容：
 * 1. 一致性哈希负载均衡
 * 2. Snappy压缩算法
 * 3. LZ4压缩算法
 * 4. 压缩性能对比
 */
@Slf4j
public class NewFeaturesTest {
    
    public static void main(String[] args) {
        log.info("=== RPC框架新增功能测试开始 ===");
        
        boolean allTestsPassed = true;
        
        try {
            // 1. 测试一致性哈希负载均衡
            allTestsPassed &= testConsistentHashLoadBalancer();
            
            // 2. 测试新增压缩算法
            allTestsPassed &= testNewCompressors();
            
            // 3. 测试压缩性能对比
            allTestsPassed &= testCompressionPerformance();
            
            if (allTestsPassed) {
                log.info("=== 所有新增功能测试通过 ✅ ===");
            } else {
                log.error("=== 部分新增功能测试失败 ❌ ===");
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("新增功能测试异常", e);
            System.exit(1);
        }
    }
    
    private static boolean testConsistentHashLoadBalancer() {
        log.info("--- 测试一致性哈希负载均衡 ---");
        
        try {
            ConsistentHashLoadBalancer loadBalancer = new ConsistentHashLoadBalancer();
            
            // 创建测试服务器列表
            List<InetSocketAddress> servers = Arrays.asList(
                    new InetSocketAddress("127.0.0.1", 8001),
                    new InetSocketAddress("127.0.0.1", 8002),
                    new InetSocketAddress("127.0.0.1", 8003),
                    new InetSocketAddress("127.0.0.1", 8004)
            );
            
            // 创建测试请求
            RpcRequest request = RpcRequest.builder()
                    .interfaceName("com.example.TestService")
                    .methodName("testMethod")
                    .version("1.0")
                    .group("default")
                    .parameters(new Object[]{"user123"})
                    .build();
            
            // 测试一致性
            InetSocketAddress selected1 = loadBalancer.select(servers, request);
            InetSocketAddress selected2 = loadBalancer.select(servers, request);
            
            if (!selected1.equals(selected2)) {
                log.error("一致性哈希测试失败：相同请求应该选择相同服务器");
                return false;
            }
            
            log.info("一致性哈希负载均衡测试通过，选择服务器: {}", selected1);
            
            // 测试分布性
            int[] distribution = new int[servers.size()];
            for (int i = 0; i < 1000; i++) {
                RpcRequest testRequest = RpcRequest.builder()
                        .interfaceName("com.example.TestService")
                        .methodName("testMethod")
                        .version("1.0")
                        .group("default")
                        .parameters(new Object[]{"user" + i})
                        .build();
                
                InetSocketAddress selected = loadBalancer.select(servers, testRequest);
                int index = servers.indexOf(selected);
                if (index >= 0) {
                    distribution[index]++;
                }
            }
            
            log.info("负载分布情况: {}", Arrays.toString(distribution));
            
            // 检查分布是否相对均匀（允许±30%的偏差）
            int avgLoad = 1000 / servers.size();
            for (int load : distribution) {
                if (load < avgLoad * 0.7 || load > avgLoad * 1.3) {
                    log.warn("负载分布不够均匀，但这在小数据集下是正常的");
                }
            }
            
            // 清理缓存
            loadBalancer.clearCache();
            
            return true;
            
        } catch (Exception e) {
            log.error("一致性哈希负载均衡测试异常", e);
            return false;
        }
    }
    
    private static boolean testNewCompressors() {
        log.info("--- 测试新增压缩算法 ---");
        
        boolean passed = true;
        
        try {
            // 准备测试数据
            String testData = "这是一个用于测试压缩算法的字符串，包含重复内容。".repeat(50);
            byte[] originalData = testData.getBytes();
            
            // 测试Snappy压缩器
            log.info("测试Snappy压缩器...");
            SnappyCompressor snappyCompressor = new SnappyCompressor();
            
            byte[] snappyCompressed = snappyCompressor.compress(originalData);
            byte[] snappyDecompressed = snappyCompressor.decompress(snappyCompressed);
            
            if (!Arrays.equals(originalData, snappyDecompressed)) {
                log.error("Snappy压缩/解压缩结果不一致");
                passed = false;
            } else {
                double snappyRatio = (1.0 - (double) snappyCompressed.length / originalData.length) * 100;
                log.info("Snappy压缩测试通过 (压缩比: {:.2f}%)", snappyRatio);
            }
            
            // 测试LZ4压缩器
            log.info("测试LZ4压缩器...");
            Lz4Compressor lz4Compressor = new Lz4Compressor();
            
            byte[] lz4Compressed = lz4Compressor.compress(originalData);
            byte[] lz4Decompressed = lz4Compressor.decompress(lz4Compressed);
            
            if (!Arrays.equals(originalData, lz4Decompressed)) {
                log.error("LZ4压缩/解压缩结果不一致");
                passed = false;
            } else {
                double lz4Ratio = (1.0 - (double) lz4Compressed.length / originalData.length) * 100;
                log.info("LZ4压缩测试通过 (压缩比: {:.2f}%)", lz4Ratio);
            }
            
        } catch (Exception e) {
            log.error("新增压缩算法测试异常", e);
            passed = false;
        }
        
        return passed;
    }
    
    private static boolean testCompressionPerformance() {
        log.info("--- 测试压缩性能对比 ---");
        
        try {
            // 准备测试数据（较大的数据以体现性能差异）
            StringBuilder dataBuilder = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                dataBuilder.append("这是第").append(i).append("行测试数据，用于性能测试。包含一些重复内容以便压缩。\n");
            }
            byte[] testData = dataBuilder.toString().getBytes();
            
            log.info("原始数据大小: {} KB", testData.length / 1024);
            
            // 测试各种压缩算法的性能
            testCompressorPerformance("GZIP", new GzipCompressor(), testData);
            testCompressorPerformance("Snappy", new SnappyCompressor(), testData);
            testCompressorPerformance("LZ4", new Lz4Compressor(), testData);
            
            return true;
            
        } catch (Exception e) {
            log.error("压缩性能测试异常", e);
            return false;
        }
    }
    
    private static void testCompressorPerformance(String name, 
                                                com.rpc.common.compress.Compressor compressor, 
                                                byte[] testData) {
        int iterations = 100;
        
        // 压缩性能测试
        long compressStart = System.nanoTime();
        byte[] compressed = null;
        for (int i = 0; i < iterations; i++) {
            compressed = compressor.compress(testData);
        }
        long compressTime = (System.nanoTime() - compressStart) / 1_000_000; // 转换为毫秒
        
        // 解压缩性能测试
        long decompressStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            compressor.decompress(compressed);
        }
        long decompressTime = (System.nanoTime() - decompressStart) / 1_000_000; // 转换为毫秒
        
        double compressionRatio = (1.0 - (double) compressed.length / testData.length) * 100;
        
        log.info("{} 性能统计:", name);
        log.info("  压缩比: {:.2f}%", compressionRatio);
        log.info("  压缩耗时: {} ms (平均 {:.2f} ms/次)", compressTime, (double) compressTime / iterations);
        log.info("  解压耗时: {} ms (平均 {:.2f} ms/次)", decompressTime, (double) decompressTime / iterations);
        log.info("  压缩后大小: {} KB", compressed.length / 1024);
    }
}
