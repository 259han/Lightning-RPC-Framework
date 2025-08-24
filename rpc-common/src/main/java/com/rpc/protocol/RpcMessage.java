package com.rpc.protocol;

import lombok.Data;

/**
 * RPC消息基类，包含协议头信息
 */
@Data
public class RpcMessage {
    /**
     * 请求ID
     */
    private long requestId;
    
    /**
     * 消息类型
     */
    private byte messageType;
    
    /**
     * 序列化类型
     */
    private byte codecType;
    
    /**
     * 压缩类型
     */
    private byte compressType;
    
    /**
     * 数据
     */
    private Object data;
}
