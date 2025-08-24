package com.rpc.common.exception;

/**
 * 序列化异常
 */
public class SerializationException extends RpcException {
    public SerializationException(String message) {
        super(message);
    }
    
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
