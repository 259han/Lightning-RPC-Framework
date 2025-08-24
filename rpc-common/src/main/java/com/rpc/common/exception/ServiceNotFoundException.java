package com.rpc.common.exception;

/**
 * 服务未找到异常
 */
public class ServiceNotFoundException extends RpcException {
    public ServiceNotFoundException(String serviceName) {
        super("服务未找到: " + serviceName);
    }
    
    public ServiceNotFoundException(String serviceName, Throwable cause) {
        super("服务未找到: " + serviceName, cause);
    }
}
