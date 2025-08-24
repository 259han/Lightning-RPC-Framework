package com.rpc.common.constants;

/**
 * RPC常量定义
 */
public class RpcConstants {
    /**
     * 魔数，用于快速识别RPC协议包
     */
    public static final byte[] MAGIC_NUMBER = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    
    /**
     * 版本号
     */
    public static final byte VERSION = 1;
    
    /**
     * 消息类型：请求
     */
    public static final byte REQUEST_TYPE = 1;
    
    /**
     * 消息类型：响应
     */
    public static final byte RESPONSE_TYPE = 2;
    
    /**
     * 头部长度：魔数(4) + 版本(1) + 总长度(4) + 消息类型(1) + 序列化类型(1) + 压缩类型(1) + 请求ID(8)
     */
    public static final int HEADER_LENGTH = 20;
    
    /**
     * 序列化类型：JSON
     */
    public static final byte SERIALIZATION_JSON = 1;
    
    /**
     * 序列化类型：Hessian
     */
    public static final byte SERIALIZATION_HESSIAN = 2;
    
    /**
     * 序列化类型：Protobuf
     */
    public static final byte SERIALIZATION_PROTOBUF = 3;
    
    /**
     * 压缩类型：不压缩
     */
    public static final byte COMPRESS_TYPE_NONE = 0;
    
    /**
     * 压缩类型：GZIP
     */
    public static final byte COMPRESS_TYPE_GZIP = 1;
    
    /**
     * 压缩类型：Snappy
     */
    public static final byte COMPRESS_TYPE_SNAPPY = 2;
    
    /**
     * 压缩类型：LZ4
     */
    public static final byte COMPRESS_TYPE_LZ4 = 3;
}
