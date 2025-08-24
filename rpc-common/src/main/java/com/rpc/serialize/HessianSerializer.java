package com.rpc.serialize;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.rpc.common.constants.RpcConstants;
import com.rpc.common.exception.SerializationException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hessian序列化实现
 * 
 * 特点：
 * - 性能优于JSON，数据更紧凑
 * - 支持跨语言
 * - 二进制格式，不可读但效率高
 */
@Slf4j
public class HessianSerializer implements Serializer {
    
    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            HessianOutput hessianOutput = new HessianOutput(bos);
            hessianOutput.writeObject(obj);
            hessianOutput.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("Hessian序列化失败: {}", obj.getClass(), e);
            throw new SerializationException("Hessian序列化失败", e);
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
            HessianInput hessianInput = new HessianInput(bis);
            Object result = hessianInput.readObject();
            return clazz.cast(result);
        } catch (IOException e) {
            log.error("Hessian反序列化失败: {}", clazz.getName(), e);
            throw new SerializationException("Hessian反序列化失败", e);
        } catch (ClassCastException e) {
            log.error("Hessian反序列化类型转换失败，期望类型: {}", clazz.getName(), e);
            throw new SerializationException("Hessian反序列化类型转换失败", e);
        }
    }
    
    @Override
    public byte getType() {
        return RpcConstants.SERIALIZATION_HESSIAN;
    }
}
