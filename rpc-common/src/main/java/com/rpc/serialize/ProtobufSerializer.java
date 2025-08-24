package com.rpc.serialize;

import com.rpc.common.constants.RpcConstants;
import com.rpc.common.exception.SerializationException;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protobuf序列化实现（基于Protostuff）
 * 
 * 特点：
 * - 性能最高，数据最紧凑
 * - 强类型检查
 * - 使用Protostuff避免预定义schema的复杂性
 */
@Slf4j
public class ProtobufSerializer implements Serializer {
    
    /**
     * Schema缓存，避免重复创建
     */
    private static final Map<Class<?>, Schema<?>> SCHEMA_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 线程本地的缓冲区，避免频繁分配
     */
    private static final ThreadLocal<LinkedBuffer> BUFFER_THREAD_LOCAL = ThreadLocal.withInitial(
            () -> LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE)
    );
    
    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        
        Class<?> clazz = obj.getClass();
        LinkedBuffer buffer = BUFFER_THREAD_LOCAL.get();
        
        try {
            @SuppressWarnings("unchecked")
            Schema<Object> schema = (Schema<Object>) getSchema(clazz);
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            log.error("Protobuf序列化失败: {}", clazz, e);
            throw new SerializationException("Protobuf序列化失败", e);
        } finally {
            buffer.clear();
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            Schema<T> schema = getSchema(clazz);
            T instance = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(data, instance, schema);
            return instance;
        } catch (Exception e) {
            log.error("Protobuf反序列化失败: {}", clazz.getName(), e);
            throw new SerializationException("Protobuf反序列化失败", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.SERIALIZATION_PROTOBUF;
    }
    
    /**
     * 获取Schema，使用缓存提高性能
     */
    @SuppressWarnings("unchecked")
    private static <T> Schema<T> getSchema(Class<T> clazz) {
        return (Schema<T>) SCHEMA_CACHE.computeIfAbsent(clazz, RuntimeSchema::createFrom);
    }
}
