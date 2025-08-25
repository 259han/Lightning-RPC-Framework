package com.rpc.server;

import com.rpc.common.constants.RpcConstants;
import com.rpc.common.exception.ServiceNotFoundException;
import com.rpc.protocol.RpcMessage;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.server.interceptor.RpcInterceptor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * RPC服务端处理器 - 支持拦截器
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final Map<String, Object> serviceMap;
    private final List<RpcInterceptor> interceptors;
    
    public RpcServerHandler(Map<String, Object> serviceMap, List<RpcInterceptor> interceptors) {
        this.serviceMap = serviceMap;
        this.interceptors = interceptors;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        // 处理请求消息
        if (msg.getMessageType() == RpcConstants.REQUEST_TYPE) {
            log.info("服务端收到请求: {}", msg.getRequestId());
            RpcMessage responseMsg = new RpcMessage();
            responseMsg.setRequestId(msg.getRequestId());
            responseMsg.setMessageType(RpcConstants.RESPONSE_TYPE);
            responseMsg.setCodecType(msg.getCodecType());
            responseMsg.setCompressType(msg.getCompressType());
            
            try {
                // 处理请求
                RpcRequest request = (RpcRequest) msg.getData();
                // 添加客户端IP信息
                String clientIp = ctx.channel().remoteAddress().toString();
                request.setClientIp(clientIp);
                
                Object result = handleRequestWithInterceptors(request);
                responseMsg.setData(RpcResponse.success(result));
            } catch (Exception e) {
                log.error("处理请求异常", e);
                responseMsg.setData(RpcResponse.fail(500, "服务器内部错误: " + e.getMessage()));
            }
            
            // 发送响应
            ctx.writeAndFlush(responseMsg);
        }
    }
    
    /**
     * 使用拦截器处理RPC请求
     *
     * @param request RPC请求
     * @return 调用结果
     * @throws Exception 调用异常
     */
    private Object handleRequestWithInterceptors(RpcRequest request) throws Exception {
        RpcResponse<Object> response = new RpcResponse<>();
        
        // 前置拦截器处理
        for (RpcInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preProcess(request, response)) {
                    // 拦截器拒绝请求，直接返回响应数据
                    if (response.getData() != null) {
                        throw new RuntimeException(response.getMessage());
                    } else {
                        throw new RuntimeException("请求被拦截器拒绝");
                    }
                }
            } catch (Exception e) {
                log.error("拦截器 {} 前置处理异常", interceptor.getClass().getSimpleName(), e);
                throw e;
            }
        }
        
        Object result = null;
        Exception exception = null;
        
        try {
            // 执行实际的业务方法调用
            result = handleRequest(request);
        } catch (Exception e) {
            exception = e;
        }
        
        // 后置拦截器处理
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            RpcInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.postProcess(request, response);
            } catch (Exception e) {
                log.error("拦截器 {} 后置处理异常", interceptor.getClass().getSimpleName(), e);
            }
        }
        
        // 如果有异常则抛出
        if (exception != null) {
            throw exception;
        }
        
        return result;
    }
    
    /**
     * 处理RPC请求
     *
     * @param request RPC请求
     * @return 调用结果
     * @throws Exception 调用异常
     */
    private Object handleRequest(RpcRequest request) throws Exception {
        // 获取服务实例
        String serviceName = request.getRpcServiceName();
        Object serviceBean = serviceMap.get(serviceName);
        if (serviceBean == null) {
            throw new ServiceNotFoundException(serviceName);
        }
        
        // 获取方法
        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();
        
        try {
            // 反射调用方法
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(serviceBean, parameters);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("方法不存在: " + methodName, e);
        } catch (InvocationTargetException e) {
            // 获取业务方法抛出的真实异常
            Throwable targetException = e.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            } else {
                throw new RuntimeException("方法调用失败", targetException);
            }
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("服务端异常", cause);
        ctx.close();
    }
}
