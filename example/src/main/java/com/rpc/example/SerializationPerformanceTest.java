package com.rpc.example;

import com.rpc.common.compress.GzipCompressor;
import com.rpc.common.compress.NoneCompressor;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.serialize.HessianSerializer;
import com.rpc.serialize.JsonSerializer;
import com.rpc.serialize.ProtobufSerializer;
import com.rpc.serialize.Serializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * RPC框架序列化和压缩性能测试
 * 
 * 测试内容：
 * 1. JSON、Hessian、Protobuf序列化性能对比
 * 2. GZIP压缩效果和性能测试
 * 3. 不同数据大小的测试场景
 * 4. 序列化正确性验证
 */
@Slf4j
public class SerializationPerformanceTest {
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestData implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private int age;
        private List<String> hobbies;
        private boolean active;
        private double score;
    }
    
    public static void main(String[] args) {
        // 创建测试数据
        TestData testData = createTestData();
        
        // 创建RPC请求对象（更接近实际使用场景）
        RpcRequest request = RpcRequest.builder()
                .interfaceName("com.example.TestService")
                .methodName("testMethod")
                .parameterTypes(new Class[]{TestData.class})
                .parameters(new Object[]{testData})
                .version("1.0")
                .group("default")
                .build();
        
        // 测试序列化器
        Serializer[] serializers = {
                new JsonSerializer(),
                new HessianSerializer(),
                new ProtobufSerializer()
        };
        
        String[] serializerNames = {"JSON", "Hessian", "Protobuf"};
        
        int iterations = 10000;
        
        log.info("=== 序列化性能测试 ({} 次迭代) ===", iterations);
        log.info("测试对象: RpcRequest (包含复杂TestData)");
        
        for (int i = 0; i < serializers.length; i++) {
            testSerializer(serializers[i], serializerNames[i], request, iterations);
        }
        
        // 测试压缩效果
        log.info("\n=== 压缩效果测试 ===");
        testCompression(request);
    }
    
    private static TestData createTestData() {
        TestData data = new TestData();
        data.setName("测试用户名称，这是一个较长的字符串用于测试压缩效果");
        data.setAge(25);
        data.setActive(true);
        data.setScore(95.8);
        
        List<String> hobbies = new ArrayList<>();
        hobbies.add("编程开发");
        hobbies.add("阅读技术书籍");
        hobbies.add("学习新技术");
        hobbies.add("开源项目贡献");
        hobbies.add("技术分享交流");
        data.setHobbies(hobbies);
        
        return data;
    }
    
    private static void testSerializer(Serializer serializer, String name, RpcRequest request, int iterations) {
        try {
            // 预热
            for (int i = 0; i < 1000; i++) {
                byte[] data = serializer.serialize(request);
                serializer.deserialize(data, RpcRequest.class);
            }
            
            // 测试序列化
            long startTime = System.nanoTime();
            byte[] serializedData = null;
            for (int i = 0; i < iterations; i++) {
                serializedData = serializer.serialize(request);
            }
            long serializeTime = System.nanoTime() - startTime;
            
            // 测试反序列化
            startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                serializer.deserialize(serializedData, RpcRequest.class);
            }
            long deserializeTime = System.nanoTime() - startTime;
            
            // 输出结果
            double serializeTimeMs = serializeTime / 1_000_000.0;
            double deserializeTimeMs = deserializeTime / 1_000_000.0;
            double totalTimeMs = serializeTimeMs + deserializeTimeMs;
            
            log.info("{} 序列化器:", name);
            log.info("  数据大小: {} bytes", serializedData.length);
            log.info("  序列化时间: {} ms (平均 {} μs/次)", 
                    String.format("%.2f", serializeTimeMs), 
                    String.format("%.2f", serializeTimeMs * 1000 / iterations));
            log.info("  反序列化时间: {} ms (平均 {} μs/次)", 
                    String.format("%.2f", deserializeTimeMs), 
                    String.format("%.2f", deserializeTimeMs * 1000 / iterations));
            log.info("  总时间: {} ms", String.format("%.2f", totalTimeMs));
            log.info("  吞吐量: {} 次/秒", String.format("%.0f", iterations * 1000.0 / totalTimeMs));
            log.info("");
            
        } catch (Exception e) {
            log.error("{} 序列化器测试失败", name, e);
        }
    }
    
    private static void testCompression(RpcRequest request) {
        JsonSerializer jsonSerializer = new JsonSerializer();
        byte[] originalData = jsonSerializer.serialize(request);
        
        GzipCompressor gzipCompressor = new GzipCompressor();
        NoneCompressor noneCompressor = new NoneCompressor();
        
        // 测试Gzip压缩
        byte[] gzipCompressed = gzipCompressor.compress(originalData);
        
        // 检查是否实际进行了压缩
        boolean wasCompressed = gzipCompressed.length < originalData.length;
        boolean isValid;
        
        if (wasCompressed) {
            // 数据被压缩了，进行解压缩测试
            byte[] gzipDecompressed = gzipCompressor.decompress(gzipCompressed);
            isValid = java.util.Arrays.equals(originalData, gzipDecompressed);
            log.info("原始数据大小: {} bytes", originalData.length);
            log.info("Gzip压缩后: {} bytes", gzipCompressed.length);
            log.info("压缩比: {}%", String.format("%.2f", (1.0 - (double) gzipCompressed.length / originalData.length) * 100));
            log.info("压缩解压缩正确性: {}", isValid ? "通过" : "失败");
        } else {
            // 数据没有被压缩（可能因为太小或压缩效果不佳）
            isValid = java.util.Arrays.equals(originalData, gzipCompressed);
            log.info("原始数据大小: {} bytes", originalData.length);
            log.info("GZIP压缩: 数据未被压缩（原因：数据太小或压缩效果不佳）");
            log.info("压缩正确性: {}", isValid ? "通过" : "失败");
        }
        
        // 创建更大的测试数据用于压缩性能测试
        StringBuilder largeDataBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeDataBuilder.append("这是一个用于测试压缩效果的重复字符串，包含中文字符以便测试压缩算法的效果。");
        }
        byte[] largeData = largeDataBuilder.toString().getBytes();
        
        log.info("\n使用更大数据测试压缩性能:");
        log.info("大数据原始大小: {} bytes", largeData.length);
        
        byte[] largeCompressed = gzipCompressor.compress(largeData);
        boolean largeWasCompressed = largeCompressed.length < largeData.length;
        
        if (largeWasCompressed) {
            log.info("大数据压缩后: {} bytes", largeCompressed.length);
            log.info("大数据压缩比: {}%", String.format("%.2f", (1.0 - (double) largeCompressed.length / largeData.length) * 100));
            
            // 测试压缩性能
            int compressionIterations = 100;
            
            long startTime = System.nanoTime();
            for (int i = 0; i < compressionIterations; i++) {
                gzipCompressor.compress(largeData);
            }
            long compressTime = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            for (int i = 0; i < compressionIterations; i++) {
                gzipCompressor.decompress(largeCompressed);
            }
            long decompressTime = System.nanoTime() - startTime;
            
            log.info("Gzip压缩性能 ({} 次):", compressionIterations);
            log.info("  压缩时间: {} ms (平均 {} μs/次)", 
                    String.format("%.2f", compressTime / 1_000_000.0), 
                    String.format("%.2f", compressTime / 1000.0 / compressionIterations));
            log.info("  解压时间: {} ms (平均 {} μs/次)", 
                    String.format("%.2f", decompressTime / 1_000_000.0), 
                    String.format("%.2f", decompressTime / 1000.0 / compressionIterations));
        } else {
            log.info("即使是大数据也未被压缩");
        }
    }
}
