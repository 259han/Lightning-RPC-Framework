package com.rpc.example;

import com.rpc.common.compress.GzipCompressor;
import com.rpc.common.compress.NoneCompressor;
import com.rpc.common.constants.RpcConstants;
import com.rpc.extension.ExtensionLoader;
import com.rpc.loadbalance.RandomLoadBalancer;
import com.rpc.loadbalance.RoundRobinLoadBalancer;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.serialize.HessianSerializer;
import com.rpc.serialize.JsonSerializer;
import com.rpc.serialize.ProtobufSerializer;
import com.rpc.serialize.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * 综合功能测试
 */
@Slf4j
public class ComprehensiveTest {
    
    public static void main(String[] args) {
        log.info("=== RPC框架综合功能测试开始 ===");
        
        boolean allTestsPassed = true;
        
        try {
            // 1. 测试序列化器
            allTestsPassed &= testSerializers();
            
            // 2. 测试压缩器
            allTestsPassed &= testCompressors();
            
            // 3. 测试SPI加载机制
            allTestsPassed &= testSPILoading();
            
            // 4. 测试负载均衡器
            allTestsPassed &= testLoadBalancers();
            
            // 5. 测试异常处理
            allTestsPassed &= testExceptionHandling();
            
            // 6. 测试协议常量
            allTestsPassed &= testProtocolConstants();
            
        } catch (Exception e) {
            log.error("测试过程中发生异常", e);
            allTestsPassed = false;
        }
        
        log.info("=== 测试结果: {} ===", allTestsPassed ? "全部通过" : "存在失败");
        System.exit(allTestsPassed ? 0 : 1);
    }
    
    /**
     * 测试所有序列化器
     */
    private static boolean testSerializers() {
        log.info("--- 测试序列化器 ---");
        
        boolean passed = true;
        Serializer[] serializers = {
            new JsonSerializer(),
            new HessianSerializer(),
            new ProtobufSerializer()
        };
        
        String[] names = {"JSON", "Hessian", "Protobuf"};
        
        // 创建测试数据
        RpcRequest testRequest = createTestRequest();
        RpcResponse<String> testResponse = RpcResponse.success("测试响应数据");
        
        for (int i = 0; i < serializers.length; i++) {
            try {
                log.info("测试 {} 序列化器...", names[i]);
                
                // 测试RpcRequest序列化
                byte[] requestData = serializers[i].serialize(testRequest);
                RpcRequest deserializedRequest = serializers[i].deserialize(requestData, RpcRequest.class);
                
                if (!compareRpcRequest(testRequest, deserializedRequest)) {
                    log.error("{} 序列化器 RpcRequest 测试失败", names[i]);
                    passed = false;
                    continue;
                }
                
                // 测试RpcResponse序列化
                byte[] responseData = serializers[i].serialize(testResponse);
                @SuppressWarnings("unchecked")
                RpcResponse<String> deserializedResponse = serializers[i].deserialize(responseData, RpcResponse.class);
                
                if (!compareRpcResponse(testResponse, deserializedResponse)) {
                    log.error("{} 序列化器 RpcResponse 测试失败", names[i]);
                    passed = false;
                    continue;
                }
                
                // 验证序列化类型
                byte expectedType = getExpectedSerializationType(i);
                if (serializers[i].getType() != expectedType) {
                    log.error("{} 序列化器类型不匹配，期望: {}, 实际: {}", 
                            names[i], expectedType, serializers[i].getType());
                    passed = false;
                    continue;
                }
                
                log.info("{} 序列化器测试通过 (数据大小: {} bytes)", names[i], requestData.length);
                
            } catch (Exception e) {
                log.error("{} 序列化器测试异常", names[i], e);
                passed = false;
            }
        }
        
        return passed;
    }
    
    /**
     * 测试压缩器
     */
    private static boolean testCompressors() {
        log.info("--- 测试压缩器 ---");
        
        boolean passed = true;
        
        try {
            // 创建测试数据
            String testData = "这是一个测试字符串，用于验证压缩功能。".repeat(50); // 重复50次确保超过压缩阈值
            byte[] originalData = testData.getBytes();
            
            // 测试无压缩
            NoneCompressor noneCompressor = new NoneCompressor();
            byte[] noneCompressed = noneCompressor.compress(originalData);
            byte[] noneDecompressed = noneCompressor.decompress(noneCompressed);
            
            if (!Arrays.equals(originalData, noneCompressed) || !Arrays.equals(originalData, noneDecompressed)) {
                log.error("无压缩器测试失败");
                passed = false;
            } else {
                log.info("无压缩器测试通过");
            }
            
            // 测试GZIP压缩
            GzipCompressor gzipCompressor = new GzipCompressor();
            byte[] gzipCompressed = gzipCompressor.compress(originalData);
            byte[] gzipDecompressed = gzipCompressor.decompress(gzipCompressed);
            
            if (!Arrays.equals(originalData, gzipDecompressed)) {
                log.error("GZIP压缩器测试失败");
                passed = false;
            } else {
                double compressionRatio = 1.0 - (double) gzipCompressed.length / originalData.length;
                log.info("GZIP压缩器测试通过 (压缩比: {}%)", String.format("%.2f", compressionRatio * 100));
            }
            
            // 测试压缩类型
            if (noneCompressor.getType() != RpcConstants.COMPRESS_TYPE_NONE) {
                log.error("无压缩器类型不匹配");
                passed = false;
            }
            
            if (gzipCompressor.getType() != RpcConstants.COMPRESS_TYPE_GZIP) {
                log.error("GZIP压缩器类型不匹配");
                passed = false;
            }
            
        } catch (Exception e) {
            log.error("压缩器测试异常", e);
            passed = false;
        }
        
        return passed;
    }
    
    /**
     * 测试SPI加载机制
     */
    private static boolean testSPILoading() {
        log.info("--- 测试SPI加载机制 ---");
        
        boolean passed = true;
        
        try {
            // 测试序列化器SPI加载
            ExtensionLoader<Serializer> serializerLoader = ExtensionLoader.getExtensionLoader(Serializer.class);
            
            Serializer jsonSerializer = serializerLoader.getExtension("json");
            Serializer hessianSerializer = serializerLoader.getExtension("hessian");
            Serializer protobufSerializer = serializerLoader.getExtension("protobuf");
            
            if (jsonSerializer == null || !(jsonSerializer instanceof JsonSerializer)) {
                log.error("JSON序列化器SPI加载失败");
                passed = false;
            }
            
            if (hessianSerializer == null || !(hessianSerializer instanceof HessianSerializer)) {
                log.error("Hessian序列化器SPI加载失败");
                passed = false;
            }
            
            if (protobufSerializer == null || !(protobufSerializer instanceof ProtobufSerializer)) {
                log.error("Protobuf序列化器SPI加载失败");
                passed = false;
            }
            
            if (passed) {
                log.info("序列化器SPI加载测试通过");
            }
            
        } catch (Exception e) {
            log.error("SPI加载测试异常", e);
            passed = false;
        }
        
        return passed;
    }
    
    /**
     * 测试负载均衡器
     */
    private static boolean testLoadBalancers() {
        log.info("--- 测试负载均衡器 ---");
        
        boolean passed = true;
        
        try {
            List<InetSocketAddress> servers = Arrays.asList(
                new InetSocketAddress("127.0.0.1", 8001),
                new InetSocketAddress("127.0.0.1", 8002),
                new InetSocketAddress("127.0.0.1", 8003)
            );
            
            RpcRequest request = createTestRequest();
            
            // 测试随机负载均衡
            RandomLoadBalancer randomLB = new RandomLoadBalancer();
            InetSocketAddress randomSelected = randomLB.select(servers, request);
            if (randomSelected == null || !servers.contains(randomSelected)) {
                log.error("随机负载均衡器测试失败");
                passed = false;
            } else {
                log.info("随机负载均衡器测试通过，选择服务器: {}", randomSelected);
            }
            
            // 测试轮询负载均衡
            RoundRobinLoadBalancer roundRobinLB = new RoundRobinLoadBalancer();
            InetSocketAddress[] selections = new InetSocketAddress[6];
            for (int i = 0; i < 6; i++) {
                selections[i] = roundRobinLB.select(servers, request);
            }
            
            // 验证轮询效果
            boolean roundRobinWorking = true;
            for (int i = 0; i < 3; i++) {
                if (!selections[i].equals(selections[i + 3])) {
                    roundRobinWorking = false;
                    break;
                }
            }
            
            if (!roundRobinWorking) {
                log.error("轮询负载均衡器测试失败");
                passed = false;
            } else {
                log.info("轮询负载均衡器测试通过");
            }
            
        } catch (Exception e) {
            log.error("负载均衡器测试异常", e);
            passed = false;
        }
        
        return passed;
    }
    
    /**
     * 测试异常处理
     */
    private static boolean testExceptionHandling() {
        log.info("--- 测试异常处理 ---");
        
        boolean passed = true;
        
        try {
            // 测试序列化异常
            JsonSerializer serializer = new JsonSerializer();
            
            try {
                // 尝试反序列化无效数据
                serializer.deserialize("invalid json".getBytes(), RpcRequest.class);
                log.error("序列化异常处理测试失败：应该抛出异常");
                passed = false;
            } catch (Exception e) {
                if (e.getMessage().contains("反序列化对象失败")) {
                    log.info("序列化异常处理测试通过");
                } else {
                    log.error("序列化异常处理测试失败：异常信息不正确");
                    passed = false;
                }
            }
            
            // 测试压缩异常处理
            GzipCompressor compressor = new GzipCompressor();
            try {
                // 尝试解压缩无效数据
                compressor.decompress("invalid gzip data".getBytes());
                log.error("压缩异常处理测试失败：应该抛出异常");
                passed = false;
            } catch (Exception e) {
                if (e.getMessage().contains("解压缩失败")) {
                    log.info("压缩异常处理测试通过");
                } else {
                    log.error("压缩异常处理测试失败：异常信息不正确");
                    passed = false;
                }
            }
            
        } catch (Exception e) {
            log.error("异常处理测试异常", e);
            passed = false;
        }
        
        return passed;
    }
    
    /**
     * 测试协议常量
     */
    private static boolean testProtocolConstants() {
        log.info("--- 测试协议常量 ---");
        
        boolean passed = true;
        
        // 验证魔数
        byte[] expectedMagic = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        if (!Arrays.equals(RpcConstants.MAGIC_NUMBER, expectedMagic)) {
            log.error("魔数常量不正确");
            passed = false;
        }
        
        // 验证版本号
        if (RpcConstants.VERSION != 1) {
            log.error("版本号常量不正确");
            passed = false;
        }
        
        // 验证头部长度
        if (RpcConstants.HEADER_LENGTH != 20) {
            log.error("头部长度常量不正确");
            passed = false;
        }
        
        if (passed) {
            log.info("协议常量测试通过");
        }
        
        return passed;
    }
    
    // 辅助方法
    private static RpcRequest createTestRequest() {
        return RpcRequest.builder()
                .interfaceName("com.example.TestService")
                .methodName("testMethod")
                .parameterTypes(new Class[]{String.class, Integer.class})
                .parameters(new Object[]{"测试参数", 123})
                .version("1.0")
                .group("default")
                .build();
    }
    
    private static boolean compareRpcRequest(RpcRequest expected, RpcRequest actual) {
        return expected.getInterfaceName().equals(actual.getInterfaceName()) &&
               expected.getMethodName().equals(actual.getMethodName()) &&
               Arrays.equals(expected.getParameterTypes(), actual.getParameterTypes()) &&
               Arrays.deepEquals(expected.getParameters(), actual.getParameters()) &&
               expected.getVersion().equals(actual.getVersion()) &&
               expected.getGroup().equals(actual.getGroup());
    }
    
    private static boolean compareRpcResponse(RpcResponse<String> expected, RpcResponse<String> actual) {
        return expected.getCode().equals(actual.getCode()) &&
               expected.getMessage().equals(actual.getMessage()) &&
               expected.getData().equals(actual.getData());
    }
    
    private static byte getExpectedSerializationType(int index) {
        switch (index) {
            case 0: return RpcConstants.SERIALIZATION_JSON;
            case 1: return RpcConstants.SERIALIZATION_HESSIAN;
            case 2: return RpcConstants.SERIALIZATION_PROTOBUF;
            default: return RpcConstants.SERIALIZATION_JSON;
        }
    }
}
