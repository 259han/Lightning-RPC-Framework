package com.rpc.example;

import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.serialize.JsonSerializer;
import com.rpc.serialize.HessianSerializer;
import com.rpc.serialize.ProtobufSerializer;
import com.rpc.common.compress.GzipCompressor;
import com.rpc.common.compress.NoneCompressor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * RPC框架基础功能测试
 * 
 * 测试内容：
 * 1. RPC请求和响应对象的序列化/反序列化
 * 2. 数据压缩和解压缩功能
 * 3. 基本的正确性验证
 */
@Slf4j
public class BasicFunctionalityTest {
    
    public static void main(String[] args) {
        log.info("=== RPC框架基础功能测试开始 ===");
        
        boolean allTestsPassed = true;
        
        try {
            // 测试RPC请求序列化
            allTestsPassed &= testRpcRequestSerialization();
            
            // 测试RPC响应序列化
            allTestsPassed &= testRpcResponseSerialization();
            
            // 测试数据压缩
            allTestsPassed &= testDataCompression();
            
            // 测试边界条件
            allTestsPassed &= testBoundaryConditions();
            
            if (allTestsPassed) {
                log.info("=== 所有基础功能测试通过 ✅ ===");
            } else {
                log.error("=== 部分基础功能测试失败 ❌ ===");
                System.exit(1);
            }
            
        } catch (Exception e) {
            log.error("基础功能测试异常", e);
            System.exit(1);
        }
    }
    
    private static boolean testRpcRequestSerialization() {
        log.info("--- 测试RPC请求序列化 ---");
        
        try {
            // 创建测试请求
            RpcRequest request = RpcRequest.builder()
                    .interfaceName("com.example.TestService")
                    .methodName("testMethod")
                    .parameterTypes(new Class[]{String.class, Integer.class})
                    .parameters(new Object[]{"test", 123})
                    .version("1.0")
                    .group("default")
                    .build();
            
            // 测试不同序列化器
            JsonSerializer jsonSerializer = new JsonSerializer();
            HessianSerializer hessianSerializer = new HessianSerializer();
            ProtobufSerializer protobufSerializer = new ProtobufSerializer();
            
            // JSON序列化测试
            byte[] jsonData = jsonSerializer.serialize(request);
            RpcRequest jsonDeserialized = jsonSerializer.deserialize(jsonData, RpcRequest.class);
            if (!validateRpcRequestEquality(request, jsonDeserialized)) {
                log.error("JSON序列化测试失败");
                return false;
            }
            log.info("JSON序列化测试通过");
            
            // Hessian序列化测试
            byte[] hessianData = hessianSerializer.serialize(request);
            RpcRequest hessianDeserialized = hessianSerializer.deserialize(hessianData, RpcRequest.class);
            if (!validateRpcRequestEquality(request, hessianDeserialized)) {
                log.error("Hessian序列化测试失败");
                return false;
            }
            log.info("Hessian序列化测试通过");
            
            // Protobuf序列化测试
            byte[] protobufData = protobufSerializer.serialize(request);
            RpcRequest protobufDeserialized = protobufSerializer.deserialize(protobufData, RpcRequest.class);
            if (!validateRpcRequestEquality(request, protobufDeserialized)) {
                log.error("Protobuf序列化测试失败");
                return false;
            }
            log.info("Protobuf序列化测试通过");
            
            return true;
            
        } catch (Exception e) {
            log.error("RPC请求序列化测试异常", e);
            return false;
        }
    }
    
    private static boolean testRpcResponseSerialization() {
        log.info("--- 测试RPC响应序列化 ---");
        
        try {
            // 创建成功响应
            RpcResponse<String> successResponse = RpcResponse.<String>builder()
                    .code(200)
                    .message("成功")
                    .data("success result")
                    .build();
            
            // 创建失败响应
            RpcResponse<String> errorResponse = RpcResponse.<String>builder()
                    .code(500)
                    .message("服务器内部错误")
                    .build();
            
            JsonSerializer serializer = new JsonSerializer();
            
            // 测试成功响应
            byte[] successData = serializer.serialize(successResponse);
            RpcResponse successDeserialized = serializer.deserialize(successData, RpcResponse.class);
            if (!validateRpcResponseEquality(successResponse, successDeserialized)) {
                log.error("成功响应序列化测试失败");
                return false;
            }
            log.info("成功响应序列化测试通过");
            
            // 测试异常响应
            byte[] errorData = serializer.serialize(errorResponse);
            RpcResponse errorDeserialized = serializer.deserialize(errorData, RpcResponse.class);
            if (!errorResponse.getCode().equals(errorDeserialized.getCode())) {
                log.error("异常响应序列化测试失败");
                return false;
            }
            log.info("异常响应序列化测试通过");
            
            return true;
            
        } catch (Exception e) {
            log.error("RPC响应序列化测试异常", e);
            return false;
        }
    }
    
    private static boolean testDataCompression() {
        log.info("--- 测试数据压缩功能 ---");
        
        try {
            // 创建测试数据
            StringBuilder largeText = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                largeText.append("这是一段重复的测试数据，用于验证压缩功能的正确性。");
            }
            byte[] originalData = largeText.toString().getBytes();
            
            GzipCompressor gzipCompressor = new GzipCompressor();
            NoneCompressor noneCompressor = new NoneCompressor();
            
            // 测试无压缩
            byte[] noneCompressed = noneCompressor.compress(originalData);
            byte[] noneDecompressed = noneCompressor.decompress(noneCompressed);
            if (!Arrays.equals(originalData, noneDecompressed)) {
                log.error("无压缩器测试失败");
                return false;
            }
            log.info("无压缩器测试通过");
            
            // 测试GZIP压缩
            byte[] gzipCompressed = gzipCompressor.compress(originalData);
            if (gzipCompressed.length < originalData.length) {
                // 数据被压缩了，测试解压缩
                byte[] gzipDecompressed = gzipCompressor.decompress(gzipCompressed);
                if (!Arrays.equals(originalData, gzipDecompressed)) {
                    log.error("GZIP压缩解压缩数据不一致");
                    return false;
                }
                double compressionRatio = (1.0 - (double) gzipCompressed.length / originalData.length) * 100;
                log.info("GZIP压缩测试通过 - 压缩比: {}%", String.format("%.2f", compressionRatio));
            } else {
                // 数据未被压缩（因为太小或效果不佳）
                if (!Arrays.equals(originalData, gzipCompressed)) {
                    log.error("GZIP压缩器返回的原数据不一致");
                    return false;
                }
                log.info("GZIP压缩测试通过 - 数据未压缩（原因：压缩效果不佳）");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("数据压缩测试异常", e);
            return false;
        }
    }
    
    private static boolean testBoundaryConditions() {
        log.info("--- 测试边界条件 ---");
        
        try {
            JsonSerializer serializer = new JsonSerializer();
            GzipCompressor compressor = new GzipCompressor();
            
            // 测试空数据
            byte[] emptyData = new byte[0];
            byte[] compressedEmpty = compressor.compress(emptyData);
            byte[] decompressedEmpty = compressor.decompress(compressedEmpty);
            if (!Arrays.equals(emptyData, decompressedEmpty)) {
                log.error("空数据压缩测试失败");
                return false;
            }
            log.info("空数据压缩测试通过");
            
            // 测试null数据
            byte[] compressedNull = compressor.compress(null);
            byte[] decompressedNull = compressor.decompress(null);
            if (compressedNull != null || decompressedNull != null) {
                log.error("null数据压缩测试失败");
                return false;
            }
            log.info("null数据压缩测试通过");
            
            // 测试小数据（低于压缩阈值）
            byte[] smallData = "test".getBytes();
            byte[] compressedSmall = compressor.compress(smallData);
            // 小数据应该不被压缩，直接返回原数据
            if (!Arrays.equals(smallData, compressedSmall)) {
                log.error("小数据压缩测试失败");
                return false;
            }
            log.info("小数据压缩测试通过");
            
            return true;
            
        } catch (Exception e) {
            log.error("边界条件测试异常", e);
            return false;
        }
    }
    
    private static boolean validateRpcRequestEquality(RpcRequest original, RpcRequest deserialized) {
        if (!original.getInterfaceName().equals(deserialized.getInterfaceName())) {
            return false;
        }
        if (!original.getMethodName().equals(deserialized.getMethodName())) {
            return false;
        }
        if (!original.getVersion().equals(deserialized.getVersion())) {
            return false;
        }
        if (!original.getGroup().equals(deserialized.getGroup())) {
            return false;
        }
        if (!Arrays.equals(original.getParameterTypes(), deserialized.getParameterTypes())) {
            return false;
        }
        if (!Arrays.deepEquals(original.getParameters(), deserialized.getParameters())) {
            return false;
        }
        return true;
    }
    
    private static boolean validateRpcResponseEquality(RpcResponse original, RpcResponse deserialized) {
        if (!original.getCode().equals(deserialized.getCode())) {
            return false;
        }
        if (original.getMessage() != null && !original.getMessage().equals(deserialized.getMessage())) {
            return false;
        }
        if (original.getData() != null && !original.getData().equals(deserialized.getData())) {
            return false;
        }
        if (original.getData() == null && deserialized.getData() != null) {
            return false;
        }
        return true;
    }
}
