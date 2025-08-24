package com.rpc.server;

import com.rpc.common.constants.RpcConstants;
import com.rpc.common.exception.ServiceNotFoundException;
import com.rpc.protocol.RpcMessage;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * RPC服务端处理器
 */
@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private final Map<String, Object> serviceMap;
    
    public RpcServerHandler(Map<String, Object> serviceMap) {
        this.serviceMap = serviceMap;
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
                Object result = handleRequest(request);
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
