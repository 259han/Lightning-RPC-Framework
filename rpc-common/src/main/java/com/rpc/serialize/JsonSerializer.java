package com.rpc.serialize;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpc.common.constants.RpcConstants;
import com.rpc.common.exception.SerializationException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * JSON序列化实现
 */
@Slf4j
public class JsonSerializer implements Serializer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Override
    public byte[] serialize(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化对象失败: {}", obj, e);
            throw new SerializationException("序列化对象失败", e);
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(data, clazz);
        } catch (IOException e) {
            log.error("反序列化对象失败: {}", clazz.getName(), e);
            throw new SerializationException("反序列化对象失败", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.SERIALIZATION_JSON;
    }
}
