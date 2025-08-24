package com.rpc.protocol.codec;

import com.rpc.common.compress.Compressor;
import com.rpc.common.constants.RpcConstants;
import com.rpc.extension.ExtensionLoader;
import com.rpc.protocol.RpcMessage;
import com.rpc.protocol.RpcRequest;
import com.rpc.protocol.RpcResponse;
import com.rpc.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * RPC消息解码器
 * <pre>
 * 协议格式：
 * +-------+----------+----------+----------+----------+----------+----------+----------+
 * | Magic | Version  | FullLen  | MsgType  | Codec    | Compress | ReqId    | Payload  |
 * | 4byte | 1byte    | 4byte    | 1byte    | 1byte    | 1byte    | 8byte    | Variable |
 * +-------+----------+----------+----------+----------+----------+----------+----------+
 * </pre>
 */
@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    public RpcMessageDecoder() {
        // 最大帧长度，长度字段偏移量，长度字段长度，长度调整值，跳过的字节数
        super(1024 * 1024, 5, 4, -9, 0);
    }
    
    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        
        try {
            // 检查魔数
            byte[] magic = new byte[4];
            frame.readBytes(magic);
            if (!Arrays.equals(magic, RpcConstants.MAGIC_NUMBER)) {
                log.error("魔数不匹配: {}", Arrays.toString(magic));
                throw new IllegalArgumentException("魔数不匹配");
            }
            
            // 检查版本
            byte version = frame.readByte();
            if (version != RpcConstants.VERSION) {
                log.error("版本不支持: {}", version);
                throw new IllegalArgumentException("版本不支持");
            }
            
            // 读取总长度
            int fullLength = frame.readInt();
            
            // 读取消息类型
            byte messageType = frame.readByte();
            
            // 读取序列化类型
            byte codecType = frame.readByte();
            
            // 读取压缩类型
            byte compressType = frame.readByte();
            
            // 读取请求ID
            long requestId = frame.readLong();
            
            // 构建RPC消息
            RpcMessage rpcMessage = new RpcMessage();
            rpcMessage.setRequestId(requestId);
            rpcMessage.setMessageType(messageType);
            rpcMessage.setCodecType(codecType);
            rpcMessage.setCompressType(compressType);
            
            // 计算负载长度
            int payloadLength = fullLength - RpcConstants.HEADER_LENGTH;
            if (payloadLength > 0) {
                byte[] payload = new byte[payloadLength];
                frame.readBytes(payload);
                
                // 解压缩数据
                if (compressType != RpcConstants.COMPRESS_TYPE_NONE) {
                    Compressor compressor = getCompressor(compressType);
                    payload = compressor.decompress(payload);
                }
                
                // 根据消息类型和序列化类型反序列化数据
                Serializer serializer = getSerializer(codecType);
                if (messageType == RpcConstants.REQUEST_TYPE) {
                    RpcRequest request = serializer.deserialize(payload, RpcRequest.class);
                    rpcMessage.setData(request);
                } else if (messageType == RpcConstants.RESPONSE_TYPE) {
                    RpcResponse<?> response = serializer.deserialize(payload, RpcResponse.class);
                    rpcMessage.setData(response);
                }
            }
            
            return rpcMessage;
        } finally {
            frame.release();
        }
    }
    
    /**
     * 根据序列化类型获取序列化器
     */
    private Serializer getSerializer(byte codecType) {
        ExtensionLoader<Serializer> loader = ExtensionLoader.getExtensionLoader(Serializer.class);
        
        // 根据类型选择对应的序列化器
        String serializerName = getSerializerName(codecType);
        Serializer serializer = loader.getExtension(serializerName);
        
        if (serializer == null) {
            // 如果找不到指定的序列化器，使用默认的JSON序列化器
            serializer = new com.rpc.serialize.JsonSerializer();
        }
        return serializer;
    }
    
    /**
     * 根据压缩类型获取压缩器
     */
    private Compressor getCompressor(byte compressType) {
        ExtensionLoader<Compressor> loader = ExtensionLoader.getExtensionLoader(Compressor.class);
        
        // 根据类型选择对应的压缩器
        String compressorName = getCompressorName(compressType);
        Compressor compressor = loader.getExtension(compressorName);
        
        if (compressor == null) {
            // 如果找不到指定的压缩器，使用无压缩
            compressor = new com.rpc.common.compress.NoneCompressor();
        }
        return compressor;
    }
    
    /**
     * 根据序列化类型码获取序列化器名称
     */
    private String getSerializerName(byte codecType) {
        switch (codecType) {
            case RpcConstants.SERIALIZATION_JSON:
                return "json";
            case RpcConstants.SERIALIZATION_HESSIAN:
                return "hessian";
            case RpcConstants.SERIALIZATION_PROTOBUF:
                return "protobuf";
            default:
                return "json";
        }
    }
    
    /**
     * 根据压缩类型码获取压缩器名称
     */
    private String getCompressorName(byte compressType) {
        switch (compressType) {
            case RpcConstants.COMPRESS_TYPE_NONE:
                return "none";
            case RpcConstants.COMPRESS_TYPE_GZIP:
                return "gzip";
            case RpcConstants.COMPRESS_TYPE_SNAPPY:
                return "snappy";
            case RpcConstants.COMPRESS_TYPE_LZ4:
                return "lz4";
            default:
                return "none";
        }
    }
}
